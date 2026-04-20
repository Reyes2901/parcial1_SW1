package com.workflow.bpm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormField {
    private String name;
    private String type;      // TEXT, NUMBER, BOOLEAN, IMAGE, SIGNATURE, GEOLOCATION
    private String label;
    private boolean required;
    private String defaultValue;
    
    // Tipos de campo como constantes
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_NUMBER = "NUMBER";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_SIGNATURE = "SIGNATURE";
    public static final String TYPE_GEOLOCATION = "GEOLOCATION";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_SELECT = "SELECT";
    public static final String TYPE_FILE = "FILE";
}