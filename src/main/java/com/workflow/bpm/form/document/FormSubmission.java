package com.workflow.bpm.form.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "form_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSubmission {

    @Id
    private String id;

    private String taskId;
    private String instanceId;
    private String nodeId;
    private String submittedBy;

    private Map<String, Object> data;     // los datos del formulario
    private List<String> attachmentUrls;  // URLs de imágenes y archivos
    private String signatureUrl;          // URL de la firma digital

    private Instant submittedAt;

    @CreatedDate
    private Instant createdAt;
}
