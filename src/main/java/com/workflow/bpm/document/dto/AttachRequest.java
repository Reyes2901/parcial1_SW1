package com.workflow.bpm.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Asocia un archivo YA existente al repositorio documental. No sube binarios.
 */
@Data
public class AttachRequest {

    @NotBlank
    private String fileId;

    @NotBlank
    private String instanceId;

    private String taskId;

    /** Clasificación opcional; por defecto ATTACHMENT. */
    private String documentType;

    private boolean required;
}
