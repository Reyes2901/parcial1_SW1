package com.workflow.bpm.workflow.engine;

import com.workflow.bpm.notification.NotificationService;
import com.workflow.bpm.policy.PolicyRepository;
import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.shared.model.Transition;
import com.workflow.bpm.workflow.document.AuditEntry;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import com.workflow.bpm.workflow.engine.executor.NodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.Notification;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngine {

    // Repositorios
    private final PolicyRepository policyRepo;
    private final ProcessInstanceRepository instanceRepo;
    private final NotificationService notificationService;
    // Evaluador de condiciones
    private final TransitionEvaluator transitionEvaluator;

    // Strategy map: Spring inyecta todos los NodeExecutor por su @Component("TIPO")
    private final Map<String, NodeExecutor> executors;

    // PUNTO DE ENTRADA — el cliente inicia un trámite
    public ProcessInstance startProcess(String definitionId,
                                        String clientId,
                                        String clientName,
                                        Map<String, Object> clientData) {

        ProcessDefinition def = policyRepo.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found: " + definitionId));

        if (!ProcessDefinition.STATUS_ACTIVE.equals(def.getStatus())) {
            throw new WorkflowException("La política no está publicada/activa. Estado actual: " + def.getStatus());
        }

        // Crear el expediente
        ProcessInstance instance = ProcessInstance.builder()
                .definitionId(definitionId)
                .definitionName(def.getName())
                .definitionVersion(def.getVersion())
                .clientId(clientId)
                .clientName(clientName)
                .clientData(clientData != null ? new HashMap<>(clientData) : new HashMap<>())
                .variables(clientData != null ? new HashMap<>(clientData) : new HashMap<>())
                .status(ProcessInstance.STATUS_IN_PROGRESS)
                .startedAt(Instant.now())
                .auditLog(new ArrayList<>())
                .build();

        instance = instanceRepo.save(instance);
        log.info(" Proceso iniciado: {} | Política: '{}' | Cliente: {}", 
                 instance.getId(), def.getName(), clientName);

        // Ejecutar desde el nodo START
        Node startNode = def.getStartNode();
        executeNode(instance, def, startNode);

        return instanceRepo.save(instance);
    }

    // ----------------------------------------------------------------
    // REANUDACIÓN — el funcionario completó su tarea
    // ----------------------------------------------------------------
    public ProcessInstance resumeAfterTask(String instanceId, String completedNodeId, 
                                           Map<String, Object> formData) {
        ProcessInstance instance = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + instanceId));
        
        ProcessDefinition def = policyRepo.findById(instance.getDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada"));

        Node completedNode = def.findNodeById(completedNodeId);
        
        // Actualizar variables con los datos del formulario
        if (formData != null) {
            formData.forEach((key, value) -> {
                if (!key.startsWith("_") && !List.of("definitionId", "clientName", "clientData").contains(key)) {
                    instance.getVariables().put(key, value);
                }
            });
        }
        
        
        // Avanzar al siguiente nodo
        advanceFrom(instance, def, completedNode);
        
        return instanceRepo.save(instance);
    }

    // ----------------------------------------------------------------
    // NÚCLEO — ejecutar un nodo y propagar hacia adelante
    // ----------------------------------------------------------------
    private void executeNode(ProcessInstance instance,
                             ProcessDefinition def,
                             Node node) {

        log.info("  ▶ Ejecutando nodo [{}] '{}'", node.getType(), node.getLabel());

        // Actualizar estado de la instancia
        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeLabel(node.getLabel());
        // Registrar inicio en el auditLog
        appendAudit(instance, node.getId(), node.getLabel(), 
                    AuditEntry.ACTION_NODE_STARTED, "system", null, null);

        // Delegar al executor correspondiente al tipo de nodo
        NodeExecutor executor = executors.get(node.getType());
        if (executor == null) {
            throw new WorkflowException("No hay executor para tipo de nodo: " + node.getType());
        }

        List<String> nextNodeIds = executor.execute(instance, def, node);
        if(!"START".equals(nextNodeIds) && !"END".equals(node.getType())) {
            notificationService.notifyInstanceAdvanced(instance);
        }
        // Si el executor devuelve nodos siguientes, continuar el recorrido
        // Si devuelve lista vacía = el motor se detiene (ACTIVITY esperando o END)
        if (!nextNodeIds.isEmpty()) {
            log.info("  → Siguientes nodos: {}", nextNodeIds);
            for (String nextId : nextNodeIds) {
                Node nextNode = def.findNodeById(nextId);
                executeNode(instance, def, nextNode);
            }
        } else {
            log.info("  ⏸ Motor detenido en nodo [{}] '{}'", node.getType(), node.getLabel());
        }
    }

    // ----------------------------------------------------------------
    // AVANZAR — desde un nodo completado hacia el siguiente
    // ----------------------------------------------------------------
    private void advanceFrom(ProcessInstance instance,
                             ProcessDefinition def,
                             Node completedNode) {
        
        List<Transition> salidas = def.getTransitionsFrom(completedNode.getId());
        
        if (salidas.isEmpty()) {
            log.info("  🏁 Nodo '{}' no tiene salidas. Fin del flujo.", completedNode.getLabel());
            return;
        }
        
        // Evaluar condiciones para encontrar la transición válida
        Transition chosenTransition = null;
        for (Transition t : salidas) {
            if (transitionEvaluator.evaluate(t.getCondition(), instance.getVariables())) {
                chosenTransition = t;
                break;
            }
        }
        
        // Si no hay condición verdadera, buscar transición por defecto
        if (chosenTransition == null) {
            chosenTransition = transitionEvaluator.findDefaultTransition(salidas);
        }
        
        if (chosenTransition == null) {
            throw new WorkflowException("No se pudo determinar la transición a tomar desde el nodo: " 
                                        + completedNode.getLabel());
        }
        
        log.info("  🔀 Transición elegida: '{}' → '{}'", 
                 chosenTransition.getLabel(), chosenTransition.getTargetId());
        
        Node siguiente = def.findNodeById(chosenTransition.getTargetId());
        executeNode(instance, def, siguiente);
    }

    // ----------------------------------------------------------------
    // AUDITORÍA — registrar evento en el historial
    // ----------------------------------------------------------------
    private void appendAudit(ProcessInstance inst, String nodeId, String nodeLabel,
                             String action, String userId, String transition,
                             Map<String, Object> formData) {
        
        AuditEntry entry = AuditEntry.builder()
                .nodeId(nodeId)
                .nodeLabel(nodeLabel)
                .action(action)
                .userId(userId != null ? userId : "system")
                .timestamp(Instant.now())
                .transitionTaken(transition)
                .formData(formData != null ? new HashMap<>(formData) : null)
                .build();
        
        if (inst.getAuditLog() == null) {
            inst.setAuditLog(new ArrayList<>());
        }
        inst.getAuditLog().add(entry);
    }

    // ----------------------------------------------------------------
    // MÉTODOS DE CONSULTA
    // ----------------------------------------------------------------
    
    /**
     * Obtiene una instancia por su ID
     */
    public ProcessInstance getInstance(String instanceId) {
        return instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + instanceId));
    }
    
    /**
     * Lista instancias por cliente
     */
    public List<ProcessInstance> getInstancesByClient(String clientId) {
        return instanceRepo.findByClientId(clientId);
    }
    
    /**
     * Lista instancias activas
     */
    public List<ProcessInstance> getActiveInstances() {
        return instanceRepo.findByStatus(ProcessInstance.STATUS_IN_PROGRESS);
    }
    
    /**
     * Cancela una instancia
     */
    public ProcessInstance cancelInstance(String instanceId, String reason) {
        ProcessInstance instance = getInstance(instanceId);
        
        if (!ProcessInstance.STATUS_IN_PROGRESS.equals(instance.getStatus())) {
            throw new WorkflowException("Solo se pueden cancelar instancias en progreso");
        }
        
        instance.setStatus(ProcessInstance.STATUS_CANCELLED);
        instance.setCompletedAt(Instant.now());
        notificationService.notifyInstanceRejected(instance,reason);
        appendAudit(instance, instance.getCurrentNodeId(), instance.getCurrentNodeLabel(),
                    AuditEntry.ACTION_CANCELLED, "system", null, Map.of("reason", reason));
        
        log.info("Instancia cancelada: {} - Razón: {}", instanceId, reason);
        instanceRepo.save(instance);
        return instance;
    }
}