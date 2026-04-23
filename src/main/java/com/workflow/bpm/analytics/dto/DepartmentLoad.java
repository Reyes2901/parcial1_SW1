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
    private String laneId;
    private String laneName;
    private long pendingTasks;
    private long inProgressTasks;
    private long completedToday;
}