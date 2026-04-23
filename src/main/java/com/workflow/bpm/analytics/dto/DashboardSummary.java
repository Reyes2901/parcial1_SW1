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
public class DashboardSummary {
    private long totalActiveInstances;
    private long totalCompletedToday;
    private long totalRejectedToday;
    private long totalOverdueTasks;
    private double globalCompletionRatePct;
    private double avgResolutionHours;
    private List<PolicyUsage> topPolicies;
    private List<BottleneckReport> activeBottlenecks;
    private List<DepartmentLoad> departmentLoad;
}