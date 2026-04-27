package com.workflow.bpm.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DepartmentTaskGroup {
    private String departmentId;
    private String departmentName;
    private List<TaskOverviewItem> tasks;
}
