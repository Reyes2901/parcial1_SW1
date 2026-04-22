package com.workflow.bpm.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummary {
    private String nodeLabel;
    private String status;
    private String assigneeId;
    private Long durationMinutes;
    private Instant completedAt;
    private Map<String, Object> formData;
}