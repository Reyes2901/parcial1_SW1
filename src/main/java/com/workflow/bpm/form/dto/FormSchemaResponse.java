package com.workflow.bpm.form.dto;

import com.workflow.bpm.shared.model.FormSchema;
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
public class FormSchemaResponse {
    private String taskId;
    private String nodeLabel;
    private String clientName;
    private String status;
    private Instant dueAt;
    private FormSchema schema;
    private Map<String, Object> existingSubmission;
}