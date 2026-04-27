package com.workflow.bpm.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class InstanceOverviewResponse {
    private String instanceId;
    private String status;
    private String definitionId;
    private String definitionName;
    private String clientId;
    private String clientName;
    private String currentNodeId;
    private String currentNodeLabel;
    private Instant startedAt;
    private Instant createdAt;
}
