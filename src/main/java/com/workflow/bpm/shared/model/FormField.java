package com.workflow.bpm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormField {
    private String name;          // clave del campo: "nroMedidor"
    private String type;          // TEXT|NUMBER|BOOLEAN|DATE|SELECT|MULTISELECT|IMAGE|SIGNATURE|GEOLOCATION|FILE
    private String label;         // etiqueta visible: "Número de medidor"
    private boolean required;

    // Validaciones para TEXT y NUMBER
    private Integer minLength;
    private Integer maxLength;
    private Double minValue;
    private Double maxValue;
    private String pattern;       // regex opcional: "^[A-Z]{2}[0-9]{6}$"

    // Para SELECT y MULTISELECT
    private List<FieldOption> options;

    // Para IMAGE y FILE
    private Long maxFileSizeBytes;  // ej: 5_000_000L = 5 MB
    private List<String> allowedTypes; // ej: ["image/jpeg","image/png"]

    private String placeholder;
    private String helpText;
    private Object defaultValue;
    private String dependsOn;     // nombre del campo del que depende (campos condicionales)
    private String dependsValue;  // valor que debe tener dependsOn para mostrar este campo
    
    // Constantes de tipos
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_NUMBER = "NUMBER";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_SELECT = "SELECT";
    public static final String TYPE_MULTISELECT = "MULTISELECT";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_SIGNATURE = "SIGNATURE";
    public static final String TYPE_GEOLOCATION = "GEOLOCATION";
    public static final String TYPE_FILE = "FILE";
}