package com.workflow.bpm.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskNotification {
    private String type;        // NEW_TASK | TASK_UPDATED | TASK_CANCELLED
    private String taskId;
    private String nodeLabel;
    private String clientName;
    private String priority;    // NORMAL | HIGH | URGENT
    private String instanceId;
    private Instant dueAt;
    private Instant timestamp;
    
    // Constantes de tipo
    public static final String TYPE_NEW_TASK = "NEW_TASK";
    public static final String TYPE_TASK_UPDATED = "TASK_UPDATED";
    public static final String TYPE_TASK_CANCELLED = "TASK_CANCELLED";
}