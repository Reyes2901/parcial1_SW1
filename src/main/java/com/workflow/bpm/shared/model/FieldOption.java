package com.workflow.bpm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldOption {
    private String value;   // "APROBADO"
    private String label;   // "Aprobado"
}