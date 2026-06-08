package com.workflow.bpm.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GrantUserRequest {

    @NotBlank
    private String userId;
}
