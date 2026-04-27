package com.workflow.bpm.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class InstanceOverviewItem {
    private String instanceId;
    private String policyId;
    private String policyName;
    private String processTypeId;
    private String status;
    private String clientId;
    private String clientName;
    private String currentNodeLabel;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private List<DepartmentTaskGroup> departments;
}
