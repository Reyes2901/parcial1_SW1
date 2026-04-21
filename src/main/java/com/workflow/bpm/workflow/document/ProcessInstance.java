package com.workflow.bpm.workflow.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "process_instances")
@CompoundIndex(name = "def_status", def = "{'definitionId': 1, 'status': 1}")
@CompoundIndex(name = "client_status", def = "{'clientId': 1, 'status': 1}")
@CompoundIndex(name = "currentNode_status", def = "{'currentNodeId': 1, 'status': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstance {

    @Id
    private String id;

    // Referencia a la política
    private String definitionId;
    private String definitionVersion;
    private String definitionName;     // snapshot — no cambia si el admin edita la política

    // Estado del motor
    private String status;             // IN_PROGRESS | COMPLETED | REJECTED | CANCELLED
    private String currentNodeId;
    private String currentNodeLabel;

    // Datos del solicitante
    private String clientId;
    private String clientName;
    
    @Builder.Default
    private Map<String, Object> clientData = new HashMap<>();

    // Variables activas del proceso — el TransitionEvaluator las lee
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    // Historial inmutable de todo lo ocurrido
    @Builder.Default
    private List<AuditEntry> auditLog = new ArrayList<>();

    // Tiempos
    private Instant startedAt;
    private Instant completedAt;
    private Instant dueDate;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
    
    // Constantes de estado
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    
    // Métodos helper
    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }
    
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
    
    public void addAuditEntry(AuditEntry entry) {
        if (auditLog == null) {
            auditLog = new ArrayList<>();
        }
        auditLog.add(entry);
    }
    
    public void setVariable(String key, Object value) {
        if (variables == null) {
            variables = new HashMap<>();
        }
        variables.put(key, value);
    }
    
    public Object getVariable(String key) {
        return variables != null ? variables.get(key) : null;
    }
}