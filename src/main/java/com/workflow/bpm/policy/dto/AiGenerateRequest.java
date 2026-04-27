package com.workflow.bpm.policy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AiGenerateRequest {
    @NotBlank
    private String prompt;
    
    private String language = "es";
    
    private List<String> existingLanes;
}