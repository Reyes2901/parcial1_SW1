package com.workflow.bpm.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStats {
    private String definitionId;
    private String definitionName;
    private long totalInstances;      // trámites iniciados
    private long completedInstances;  // trámites completados
    private long rejectedInstances;   // trámites rechazados
    private long activeInstances;     // trámites en curso
    private double completionRatePct; // % de completados
    private double avgTotalDurationHours; // duración promedio total
    private List<NodeStats> nodeStats; // detalle por nodo
    private String bottleneckNodeId;   // el nodo más lento
    private String bottleneckNodeLabel;
}