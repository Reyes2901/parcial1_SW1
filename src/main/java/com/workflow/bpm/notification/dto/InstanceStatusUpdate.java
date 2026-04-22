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
public class InstanceStatusUpdate {
    private String type;             // INSTANCE_ADVANCED | INSTANCE_COMPLETED | INSTANCE_REJECTED
    private String instanceId;
    private String currentNodeLabel;
    private String status;           // IN_PROGRESS | COMPLETED | REJECTED
    private int progressPct;         // 0-100 para barra de progreso en UI
    private Instant timestamp;
    
    // Constantes de tipo
    public static final String TYPE_INSTANCE_ADVANCED = "INSTANCE_ADVANCED";
    public static final String TYPE_INSTANCE_COMPLETED = "INSTANCE_COMPLETED";
    public static final String TYPE_INSTANCE_REJECTED = "INSTANCE_REJECTED";
}