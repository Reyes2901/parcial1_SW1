package com.workflow.bpm.shared.exception;

import java.util.List;

public class FormValidationException extends RuntimeException {
    private final List<String> errors;
    
    public FormValidationException(List<String> errors) {
        super("Errores de validación: " + String.join(", ", errors));
        this.errors = errors;
    }
    
    public List<String> getErrors() { 
        return errors; 
    }
}