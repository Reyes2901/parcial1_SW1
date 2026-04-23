package com.workflow.bpm.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BottleneckReport {
    private String taskId;
    private String instanceId;
    private String nodeLabel;
    private String laneName;
    private String assigneeId;
    private long overdueMinutes;
    private String priority;
    private Instant dueAt;
}