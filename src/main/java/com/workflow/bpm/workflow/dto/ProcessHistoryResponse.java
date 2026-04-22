package com.workflow.bpm.workflow.dto;

import com.workflow.bpm.workflow.document.AuditEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessHistoryResponse {
    private String instanceId;
    private String definitionName;
    private String status;
    private String clientName;
    private Instant startedAt;
    private Instant completedAt;
    private int progressPct;
    private List<AuditEntry> auditLog;
    private List<TaskSummary> tasks;
}