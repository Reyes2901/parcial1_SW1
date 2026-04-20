package com.workflow.bpm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSchema {
    @Builder.Default
    private List<FormField> fields = new ArrayList<>();
    
    // Métodos helper
    public void addField(FormField field) {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        fields.add(field);
    }
    
    public boolean hasRequiredFields() {
        return fields != null && fields.stream().anyMatch(FormField::isRequired);
    }
}