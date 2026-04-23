package com.workflow.bpm.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeStats {
    private String nodeId;
    private String nodeLabel;
    private String laneId;
    private String laneName;
    private long totalTasks;          // cuántas veces se ejecutó este nodo
    private double avgDurationMinutes; // tiempo promedio de atención
    private double minDurationMinutes; // tiempo mínimo histórico
    private double maxDurationMinutes; // tiempo máximo histórico
    private long overdueCount;         // cuántas veces se venció el plazo
    private double overdueRatePct;     // % de veces que se venció
    private double estimatedVsRealRatio; // real/estimado: >1 = más lento de lo esperado
}