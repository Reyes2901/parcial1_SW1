package com.workflow.bpm.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyUsage {
    private String definitionId;
    private String definitionName;
    private long totalInstances;
    private long activeInstances;
}