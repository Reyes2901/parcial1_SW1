package com.workflow.bpm.workflow.engine;

import com.workflow.bpm.notification.NotificationService;
import com.workflow.bpm.policy.PolicyRepository;
import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.shared.model.Transition;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngine {

    private final PolicyRepository policyRepo;
    private final ProcessInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskRepo;
    private final NotificationService notificationService;
    private final TransitionEvaluator transitionEvaluator;

    // Strategy map: Spring injects all NodeExecutors by their @Component("TYPE") name
    private final Map<String, NodeExecutor> executors;

    // ────────────────────────────────────────────────────────────────
    // START PROCESS
    // ────────────────────────────────────────────────────────────────
    public ProcessInstance startProcess(String definitionId,
                                        String clientId,
                                        String clientName,
                                        Map<String, Object> clientData) {

        ProcessDefinition def = policyRepo.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found: " + definitionId));

        if (!ProcessDefinition.STATUS_ACTIVE.equals(def.getStatus())) {
            throw new WorkflowException("La política no está publicada/activa. Estado: " + def.getStatus());
        }

        ProcessInstance instance = ProcessInstance.builder()
                .definitionId(definitionId)
                .definitionName(def.getName())
                .definitionVersion(def.getVersion())
                .processTypeId(def.getProcessTypeId())
                .clientId(clientId)
                .clientName(clientName)
                .clientData(clientData != null ? new HashMap<>(clientData) : new HashMap<>())
                .variables(clientData != null ? new HashMap<>(clientData) : new HashMap<>())
                .status(ProcessInstance.STATUS_IN_PROGRESS)
                .startedAt(Instant.now())
                .auditLog(new ArrayList<>())
                .build();

        instance = instanceRepo.save(instance);
        log.info("▶ Proceso iniciado: {} | Política: '{}' | Cliente: {}",
                 instance.getId(), def.getName(), clientName);

        // Execute from the START node
        Node startNode = def.getStartNode();
        executeNode(instance, def, startNode);

        return instanceRepo.save(instance);
    }

    // ────────────────────────────────────────────────────────────────
    // RESUME AFTER TASK COMPLETION
    // ────────────────────────────────────────────────────────────────
    /**
     * Called by TaskService once a TaskInstance is marked COMPLETED.
     *
     * Parallel-aware logic:
     *   - If the completed task has a parallelGroupId, try to advance
     *     through the JOIN node that follows the parallel branch.
     *   - Otherwise, advance sequentially from the completed node.
     */
    public ProcessInstance resumeAfterTask(String instanceId,
                                           String completedNodeId,
                                           Map<String, Object> formData) {

        ProcessInstance instance = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + instanceId));

        ProcessDefinition def = policyRepo.findById(instance.getDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada"));

        // Merge form data into process variables
        if (formData != null) {
            formData.forEach((key, value) -> {
                if (!key.startsWith("_") && !List.of("definitionId", "clientName", "clientData").contains(key)) {
                    instance.getVariables().put(key, value);
                }
            });
        }

        Node completedNode = def.findNodeById(completedNodeId);

        // Check if this task was part of a parallel group
        String parallelGroupId = findParallelGroupId(instance, completedNodeId);

        if (parallelGroupId != null) {
            // Parallel branch completed — try the JOIN node
            advanceThroughJoin(instance, def, completedNode, parallelGroupId);
        } else {
            // Sequential flow
            advanceFrom(instance, def, completedNode);
        }

        return instanceRepo.save(instance);
    }

    // ────────────────────────────────────────────────────────────────
    // CORE — execute a node and propagate
    // ────────────────────────────────────────────────────────────────
    private void executeNode(ProcessInstance instance,
                             ProcessDefinition def,
                             Node node) {

        log.info("  ▶ Ejecutando nodo [{}] '{}'", node.getType(), node.getLabel());

        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeLabel(node.getLabel());

        appendAudit(instance, node.getId(), node.getLabel(),
                    AuditEntry.ACTION_NODE_STARTED, "system", null, null);

        NodeExecutor executor = getExecutor(node.getType());
        List<String> nextNodeIds = executor.execute(instance, def, node);

        if (!"START".equals(node.getType()) && !"END".equals(node.getType())) {
            notificationService.notifyInstanceAdvanced(instance);
        }

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

    // ────────────────────────────────────────────────────────────────
    // ADVANCE — sequential flow from a completed node
    // ────────────────────────────────────────────────────────────────
    private void advanceFrom(ProcessInstance instance,
                             ProcessDefinition def,
                             Node completedNode) {

        List<Transition> salidas = def.getTransitionsFrom(completedNode.getId());

        if (salidas.isEmpty()) {
            log.info("  🏁 Nodo '{}' no tiene salidas. Fin del flujo.", completedNode.getLabel());
            return;
        }

        // Evaluate conditions
        Transition chosenTransition = null;
        for (Transition t : salidas) {
            if (transitionEvaluator.evaluate(t.getCondition(), instance.getVariables())) {
                chosenTransition = t;
                break;
            }
        }

        if (chosenTransition == null) {
            chosenTransition = transitionEvaluator.findDefaultTransition(salidas);
        }

        if (chosenTransition == null) {
            throw new WorkflowException("No se pudo determinar la transición desde: "
                                        + completedNode.getLabel());
        }

        log.info("  🔀 Transición: '{}' → '{}'",
                 chosenTransition.getLabel(), chosenTransition.getTargetId());

        Node siguiente = def.findNodeById(chosenTransition.getTargetId());
        executeNode(instance, def, siguiente);
    }

    // ────────────────────────────────────────────────────────────────
    // ADVANCE THROUGH JOIN (parallel branch completion)
    // ────────────────────────────────────────────────────────────────
    /**
     * After a parallel branch task completes, find the JOIN node that
     * follows it and execute it. The JoinNodeExecutor will decide whether
     * to halt (siblings still pending) or continue.
     */
    private void advanceThroughJoin(ProcessInstance instance,
                                    ProcessDefinition def,
                                    Node completedNode,
                                    String parallelGroupId) {

        // Find the JOIN node reachable from the completed node
        Node joinNode = findNextJoinNode(def, completedNode.getId());

        if (joinNode != null) {
            log.info("  🔗 Parallel branch '{}' done — evaluating JOIN '{}'",
                     completedNode.getLabel(), joinNode.getLabel());
            executeNode(instance, def, joinNode);
        } else {
            // No JOIN found — fall back to sequential advance
            log.warn("  ⚠ No JOIN node found after parallel branch '{}'. Advancing sequentially.",
                     completedNode.getLabel());
            advanceFrom(instance, def, completedNode);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // GRAPH HELPERS
    // ────────────────────────────────────────────────────────────────

    /**
     * Walks transitions forward from a node until it finds the first JOIN node.
     */
    private Node findNextJoinNode(ProcessDefinition def, String fromNodeId) {
        return findJoinForward(def, fromNodeId, 0);
    }

    private Node findJoinForward(ProcessDefinition def, String nodeId, int depth) {
        if (depth > 30) return null;

        List<Transition> outgoing = def.getTransitionsFrom(nodeId);
        for (Transition t : outgoing) {
            Node target = def.findNodeByIdSafe(t.getTargetId()).orElse(null);
            if (target == null) continue;
            if (Node.TYPE_JOIN.equals(target.getType())) return target;
            Node found = findJoinForward(def, target.getId(), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Resolves the parallelGroupId of the most recent completed TaskInstance
     * for a given node in the given process instance.
     */
    private String findParallelGroupId(ProcessInstance instance, String nodeId) {
        return taskRepo.findByInstanceId(instance.getId()).stream()
                .filter(t -> nodeId.equals(t.getNodeId())
                          && TaskInstance.STATUS_COMPLETED.equals(t.getStatus())
                          && t.getParallelGroupId() != null)
                .map(TaskInstance::getParallelGroupId)
                .findFirst()
                .orElse(null);
    }

    private NodeExecutor getExecutor(String nodeType) {
        NodeExecutor executor = executors.get(nodeType);
        if (executor == null) {
            throw new WorkflowException("No hay executor para tipo de nodo: " + nodeType);
        }
        return executor;
    }

    // ────────────────────────────────────────────────────────────────
    // AUDIT
    // ────────────────────────────────────────────────────────────────
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

    // ────────────────────────────────────────────────────────────────
    // QUERY METHODS
    // ────────────────────────────────────────────────────────────────

    public ProcessInstance getInstance(String instanceId) {
        return instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + instanceId));
    }

    public List<ProcessInstance> getInstancesByClient(String clientId) {
        return instanceRepo.findByClientId(clientId);
    }

    public List<ProcessInstance> getActiveInstances() {
        return instanceRepo.findByStatus(ProcessInstance.STATUS_IN_PROGRESS);
    }

    public ProcessInstance cancelInstance(String instanceId, String reason) {
        ProcessInstance instance = getInstance(instanceId);

        if (!ProcessInstance.STATUS_IN_PROGRESS.equals(instance.getStatus())) {
            throw new WorkflowException("Solo se pueden cancelar instancias en progreso");
        }

        instance.setStatus(ProcessInstance.STATUS_CANCELLED);
        instance.setCompletedAt(Instant.now());
        notificationService.notifyInstanceRejected(instance, reason);
        appendAudit(instance, instance.getCurrentNodeId(), instance.getCurrentNodeLabel(),
                    AuditEntry.ACTION_CANCELLED, "system", null, Map.of("reason", reason));

        log.info("Instancia cancelada: {} - Razón: {}", instanceId, reason);
        instanceRepo.save(instance);
        return instance;
    }
}