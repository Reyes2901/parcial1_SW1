package com.workflow.bpm.policy.dto;

import java.time.Instant;

public record PolicySummaryDTO(
        String id,
        String name,
        String status,
        String version,
        String description,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        int nodeCount,
        int laneCount,
        int transitionCount
) {}
