package com.workflow.bpm.task.document;

import com.workflow.bpm.shared.model.FormSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "task_instances")
@CompoundIndex(name = "assignee_status", def = "{'assigneeId': 1, 'status': 1}")
@CompoundIndex(name = "instance_tasks", def = "{'instanceId': 1, 'nodeId': 1}")
@CompoundIndex(name = "dueAt_status", def = "{'dueAt': 1, 'status': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstance {

    @Id
    private String id;

    // Contexto del proceso
    private String instanceId;
    private String definitionId;
    private String nodeId;
    private String nodeLabel;
    private String laneId;

    // Responsable
    private String assigneeId;
    private String assigneeRole;     // ADMIN | FUNCIONARIO | TECNICO, etc.

    // Estado
    private String status;           // PENDING | IN_PROGRESS | COMPLETED | REJECTED
    private String priority;         // LOW | NORMAL | HIGH | URGENT

    // Formulario — copia inmutable del formSchema del nodo
    private FormSchema formSchema;
    private Map<String, Object> formSubmission;  // lo que llenó el funcionario

    // Snapshot del cliente para mostrar en bandeja sin joins
    private String clientName;
    private String clientAddress;

    // Tiempos
    private Instant createdAt;
    private Instant dueAt;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMinutes;
    
    // Constantes de estado
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REJECTED = "REJECTED";
    
    // Constantes de prioridad
    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";
    
    // Métodos helper
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
    
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
    
    public void start() {
        this.status = STATUS_IN_PROGRESS;
        this.startedAt = Instant.now();
    }
    
    public void complete(Map<String, Object> submission) {
        this.status = STATUS_COMPLETED;
        this.formSubmission = submission;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationMinutes = java.time.Duration.between(startedAt, completedAt).toMinutes();
        }
    }
    
    public void reject(String reason) {
        this.status = STATUS_REJECTED;
        this.completedAt = Instant.now();
    }
}