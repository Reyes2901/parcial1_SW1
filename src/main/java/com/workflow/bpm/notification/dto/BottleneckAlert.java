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
public class BottleneckAlert {
    private String type;            // BOTTLENECK_DETECTED
    private String taskId;
    private String instanceId;
    private String nodeLabel;
    private String assigneeId;
    private long overdueMinutes;    // cuánto tiempo lleva vencida
    private Instant timestamp;
    
    // Constantes de tipo
    public static final String TYPE_BOTTLENECK_DETECTED = "BOTTLENECK_DETECTED";
}