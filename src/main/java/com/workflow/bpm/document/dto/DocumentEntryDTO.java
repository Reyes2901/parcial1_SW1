package com.workflow.bpm.document.dto;

import com.workflow.bpm.document.DocumentRepositoryEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Vista plana de una entrada del repositorio documental (evita serializar la
 * entidad directamente, conforme a la regla 7 de AGENTS.md).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntryDTO {

    private String id;
    private String fileId;
    private String processInstanceId;
    private String policyId;
    private String taskId;
    private String uploadedBy;
    private Instant uploadedAt;
    private String documentType;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private boolean required;
    private int version;
    private Instant createdAt;

    public static DocumentEntryDTO from(DocumentRepositoryEntry e) {
        return DocumentEntryDTO.builder()
                .id(e.getId())
                .fileId(e.getFileId())
                .processInstanceId(e.getProcessInstanceId())
                .policyId(e.getPolicyId())
                .taskId(e.getTaskId())
                .uploadedBy(e.getUploadedBy())
                .uploadedAt(e.getUploadedAt())
                .documentType(e.getDocumentType())
                .originalFilename(e.getOriginalFilename())
                .mimeType(e.getMimeType())
                .fileSize(e.getFileSize())
                .required(e.isRequired())
                .version(e.getVersion())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
