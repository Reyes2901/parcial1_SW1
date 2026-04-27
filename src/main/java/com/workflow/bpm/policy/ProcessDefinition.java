package com.workflow.bpm.policy;

import com.workflow.bpm.shared.model.Lane;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.shared.model.Transition;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Document(collection = "process_definitions")
@CompoundIndex(def = "{'status': 1, 'createdBy': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinition {

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String name;

    private String version;      // "1.0", "1.1"...
    private String status;       // DRAFT, ACTIVE, ARCHIVED
    private String createdBy;    // userId
    private String description;
    private String processTypeId; // Referencia a ProcessType

    @Builder.Default
    private List<String> departmentIds = new ArrayList<>(); // Departamentos asociados

    @Builder.Default
    private List<Lane> lanes = new ArrayList<>();

    @Builder.Default
    private List<Node> nodes = new ArrayList<>();

    @Builder.Default
    private List<Transition> transitions = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // Constantes de estado
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    // --- Métodos helper para el WorkflowEngine (Fase 3) ---

    /**
     * Busca un nodo por su ID
     */
    public Node findNodeById(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Node not found: " + nodeId));
    }

    /**
     * Busca un nodo por su ID de forma segura (Optional)
     */
    public Optional<Node> findNodeByIdSafe(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst();
    }

    /**
     * Obtiene todas las transiciones que salen de un nodo
     */
    public List<Transition> getTransitionsFrom(String nodeId) {
        return transitions.stream()
                .filter(t -> t.getSourceId().equals(nodeId))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene el nodo inicial (START)
     */
    public Node getStartNode() {
        return nodes.stream()
                .filter(n -> Node.TYPE_START.equals(n.getType()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No START node found in process: " + name));
    }

    /**
     * Obtiene todos los nodos de tipo END
     */
    public List<Node> getEndNodes() {
        return nodes.stream()
                .filter(n -> Node.TYPE_END.equals(n.getType()))
                .collect(Collectors.toList());
    }

    /**
     * Busca un carril por su ID
     */
    public Optional<Lane> findLaneById(String laneId) {
        return lanes.stream()
                .filter(l -> l.getId().equals(laneId))
                .findFirst();
    }

    /**
     * Obtiene el carril asociado a un nodo
     */
    public Optional<Lane> findLaneForNode(String nodeId) {
        return findNodeByIdSafe(nodeId)
                .flatMap(node -> findLaneById(node.getLaneId()));
    }

    /**
     * Verifica si el proceso está activo
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /**
     * Verifica si el proceso está en borrador
     */
    public boolean isDraft() {
        return STATUS_DRAFT.equals(status);
    }

    /**
     * Verifica si el proceso está archivado
     */
    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }

    /**
     * Obtiene el número total de nodos
     */
    public int getTotalNodes() {
        return nodes != null ? nodes.size() : 0;
    }

    /**
     * Obtiene el número total de transiciones
     */
    public int getTotalTransitions() {
        return transitions != null ? transitions.size() : 0;
    }

    /**
     * Valida si el grafo tiene una estructura básica válida
     */
    public boolean hasValidStructure() {
        if (nodes == null || nodes.isEmpty()) return false;
        
        boolean hasStart = nodes.stream().anyMatch(n -> Node.TYPE_START.equals(n.getType()));
        boolean hasEnd = nodes.stream().anyMatch(n -> Node.TYPE_END.equals(n.getType()));
        
        return hasStart && hasEnd;
    }

    /**
     * Obtiene todos los nodos de un tipo específico
     */
    public List<Node> getNodesByType(String type) {
        return nodes.stream()
                .filter(n -> type.equals(n.getType()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los nodos de un carril específico
     */
    public List<Node> getNodesByLane(String laneId) {
        return nodes.stream()
                .filter(n -> laneId.equals(n.getLaneId()))
                .collect(Collectors.toList());
    }
}