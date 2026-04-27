package com.workflow.bpm.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentLoad {
    private String departmentId;
    private String departmentName;
    private String laneId;       // kept for backward compatibility
    private String laneName;     // kept for backward compatibility
    private long pendingTasks;
    private long inProgressTasks;
    private long completedToday;
}