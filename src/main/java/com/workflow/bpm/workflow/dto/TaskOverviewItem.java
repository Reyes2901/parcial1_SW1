package com.workflow.bpm.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TaskOverviewItem {
    private String taskId;
    private String nodeId;
    private String name;
    private String status;
    private String assignedUserId;
    private String assigneeRole;
    private String parallelGroupId;
    private String priority;
    private Instant dueAt;
    private Instant createdAt;
    private Instant completedAt;
}
