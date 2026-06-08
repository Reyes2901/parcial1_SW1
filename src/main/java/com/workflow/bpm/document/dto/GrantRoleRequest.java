package com.workflow.bpm.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GrantRoleRequest {

    @NotBlank
    private String role;
}
