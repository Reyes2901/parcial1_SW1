package com.workflow.bpm.policy;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.shared.exception.ValidationException;
import com.workflow.bpm.shared.model.Node;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {
    
    private final PolicyRepository policyRepository;
    
    public ProcessDefinition create(ProcessDefinition policy, String username) {
        // Validar nombre único
        if (policyRepository.existsByName(policy.getName())) {
            throw new ValidationException("Ya existe una política con el nombre: " + policy.getName());
        }
        
        // Validar estructura del grafo
        validateGraph(policy);
        
        // Configurar metadata
        policy.setStatus(ProcessDefinition.STATUS_DRAFT);
        policy.setCreatedBy(username);
        policy.setCreatedAt(Instant.now());
        policy.setUpdatedAt(Instant.now());
        policy.setVersion("1.0.0");
        
        ProcessDefinition saved = policyRepository.save(policy);
        log.info("Política creada: {} por {}", saved.getName(), username);
        return saved;
    }
    
    public ProcessDefinition update(String id, ProcessDefinition updatedPolicy) {
        ProcessDefinition existing = policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada: " + id));
        
        // Validar estructura
        validateGraph(updatedPolicy);
        
        // Actualizar campos
        existing.setName(updatedPolicy.getName());
        existing.setDescription(updatedPolicy.getDescription());
        existing.setLanes(updatedPolicy.getLanes());
        existing.setNodes(updatedPolicy.getNodes());
        existing.setTransitions(updatedPolicy.getTransitions());
        existing.setMetadata(updatedPolicy.getMetadata());
        existing.setUpdatedAt(Instant.now());
        
        ProcessDefinition saved = policyRepository.save(existing);
        log.info("Política actualizada: {}", saved.getName());
        return saved;
    }
    
    public ProcessDefinition activate(String id) {
        ProcessDefinition policy = policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada: " + id));
        
        validateGraph(policy);
        policy.setStatus(ProcessDefinition.STATUS_ACTIVE);
        policy.setUpdatedAt(Instant.now());
        
        ProcessDefinition saved = policyRepository.save(policy);
        log.info("Política activada: {}", saved.getName());
        return saved;
    }
    
    public List<ProcessDefinition> findAll() {
        return policyRepository.findAll();
    }
    
    public List<ProcessDefinition> findActive() {
        return policyRepository.findByStatus(ProcessDefinition.STATUS_ACTIVE);
    }
    
    public ProcessDefinition findById(String id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada: " + id));
    }
    
    public void delete(String id) {
        ProcessDefinition policy = findById(id);
        if (ProcessDefinition.STATUS_ACTIVE.equals(policy.getStatus())) {
            throw new ValidationException("No se puede eliminar una política activa. Archívela primero.");
        }
        policyRepository.deleteById(id);
        log.info("Política eliminada: {}", policy.getName());
    }
    
    // Validación del grafo
    private void validateGraph(ProcessDefinition policy) {
        if (policy.getNodes() == null || policy.getNodes().isEmpty()) {
            throw new ValidationException("El proceso debe tener al menos un nodo");
        }
        
        // Validar que exista un nodo START
        boolean hasStart = policy.getNodes().stream()
                .anyMatch(node -> Node.TYPE_START.equals(node.getType()));
        if (!hasStart) {
            throw new ValidationException("El proceso debe tener un nodo START");
        }
        
        // Validar que exista al menos un nodo END
        boolean hasEnd = policy.getNodes().stream()
                .anyMatch(node -> Node.TYPE_END.equals(node.getType()));
        if (!hasEnd) {
            throw new ValidationException("El proceso debe tener al menos un nodo END");
        }
        
        // Validar que todos los sourceId y targetId de transiciones existan
        if (policy.getTransitions() != null && !policy.getTransitions().isEmpty()) {
            Set<String> nodeIds = policy.getNodes().stream()
                    .map(Node::getId)
                    .collect(Collectors.toSet());
            
            for (var transition : policy.getTransitions()) {
                if (transition.getSourceId() != null && !nodeIds.contains(transition.getSourceId())) {
                    throw new ValidationException("Transición con sourceId inválido: " + transition.getSourceId());
                }
                if (transition.getTargetId() != null && !nodeIds.contains(transition.getTargetId())) {
                    throw new ValidationException("Transición con targetId inválido: " + transition.getTargetId());
                }
            }
            
            // Validar que no haya nodos huérfanos (excepto START)
            Set<String> nodesWithIncoming = policy.getTransitions().stream()
                    .map(t -> t.getTargetId())
                    .collect(Collectors.toSet());
            
            for (var node : policy.getNodes()) {
                if (!Node.TYPE_START.equals(node.getType()) && !nodesWithIncoming.contains(node.getId())) {
                    log.warn("Nodo huérfano detectado (sin transiciones entrantes): {}", node.getId());
                }
            }
        }
    }
}