package com.workflow.bpm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transition {
    private String id;
    private String sourceId;
    private String targetId;
    private String condition;   // SpEL: "deuda == false" o "aprobado == true"
    private String label;
    
    // Métodos helper
    public boolean hasCondition() {
        return condition != null && !condition.trim().isEmpty();
    }
    
    public boolean isDefault() {
        return !hasCondition();
    }
}