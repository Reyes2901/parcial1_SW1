package com.workflow.bpm.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class StartProcessRequest {
    @NotBlank
    private String definitionId;
    
    @NotBlank
    private String clientName;
    
    private Map<String, Object> clientData = new HashMap<>();
}