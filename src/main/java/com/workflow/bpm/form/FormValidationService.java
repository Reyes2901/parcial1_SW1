package com.workflow.bpm.form;

import com.workflow.bpm.shared.exception.FormValidationException;
import com.workflow.bpm.shared.model.FieldOption;
import com.workflow.bpm.shared.model.FormField;
import com.workflow.bpm.shared.model.FormSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FormValidationService {

    /**
     * Valida que formData cumpla todas las reglas del formSchema.
     * Lanza FormValidationException con lista de errores si algo falla.
     */
    public void validate(FormSchema schema, Map<String, Object> formData) {
        if (schema == null || schema.getFields() == null) return;

        List<String> errors = new ArrayList<>();

        for (FormField field : schema.getFields()) {
            Object value = formData.get(field.getName());

            // Verificar campo obligatorio
            if (field.isRequired() && isEmpty(value)) {
                errors.add("Campo requerido: '" + field.getLabel() + "'");
                continue;
            }
            if (isEmpty(value)) continue;

            // Validar por tipo
            switch (field.getType()) {
                case FormField.TYPE_TEXT -> validateText(field, value, errors);
                case FormField.TYPE_NUMBER -> validateNumber(field, value, errors);
                case FormField.TYPE_BOOLEAN -> validateBoolean(field, value, errors);
                case FormField.TYPE_DATE -> validateDate(field, value, errors);
                case FormField.TYPE_SELECT -> validateSelect(field, value, errors);
                case FormField.TYPE_MULTISELECT -> validateMultiSelect(field, value, errors);
                case FormField.TYPE_IMAGE, FormField.TYPE_FILE -> validateFile(field, value, errors);
                case FormField.TYPE_SIGNATURE -> validateSignature(field, value, errors);
                case FormField.TYPE_GEOLOCATION -> validateGeolocation(field, value, errors);
            }
        }

        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
    }

    private void validateText(FormField f, Object value, List<String> errors) {
        String s = value.toString();
        if (f.getMinLength() != null && s.length() < f.getMinLength())
            errors.add("'" + f.getLabel() + "' debe tener al menos " + f.getMinLength() + " caracteres");
        if (f.getMaxLength() != null && s.length() > f.getMaxLength())
            errors.add("'" + f.getLabel() + "' no puede superar " + f.getMaxLength() + " caracteres");
        if (f.getPattern() != null && !s.matches(f.getPattern()))
            errors.add("'" + f.getLabel() + "' no tiene el formato correcto");
    }

    private void validateNumber(FormField f, Object value, List<String> errors) {
        try {
            double n = Double.parseDouble(value.toString());
            if (f.getMinValue() != null && n < f.getMinValue())
                errors.add("'" + f.getLabel() + "' debe ser mayor o igual a " + f.getMinValue());
            if (f.getMaxValue() != null && n > f.getMaxValue())
                errors.add("'" + f.getLabel() + "' debe ser menor o igual a " + f.getMaxValue());
        } catch (NumberFormatException e) {
            errors.add("'" + f.getLabel() + "' debe ser un número válido");
        }
    }

    private void validateBoolean(FormField f, Object value, List<String> errors) {
        if (!(value instanceof Boolean) && !value.toString().matches("(?i)true|false"))
            errors.add("'" + f.getLabel() + "' debe ser verdadero o falso");
    }

    private void validateDate(FormField f, Object value, List<String> errors) {
        try { 
            Instant.parse(value.toString()); 
        } catch (Exception e) {
            errors.add("'" + f.getLabel() + "' debe ser una fecha válida (ISO 8601)");
        }
    }

    private void validateSelect(FormField f, Object value, List<String> errors) {
        if (f.getOptions() == null) return;
        boolean valid = f.getOptions().stream()
                .anyMatch(o -> o.getValue().equals(value.toString()));
        if (!valid)
            errors.add("'" + f.getLabel() + "': opción no válida");
    }

    @SuppressWarnings("unchecked")
    private void validateMultiSelect(FormField f, Object value, List<String> errors) {
        if (!(value instanceof List)) {
            errors.add("'" + f.getLabel() + "' debe ser una lista");
            return;
        }
        if (f.getOptions() == null) return;
        Set<String> valid = f.getOptions().stream()
                .map(FieldOption::getValue).collect(Collectors.toSet());
        ((List<?>) value).forEach(v -> {
            if (!valid.contains(v.toString()))
                errors.add("'" + f.getLabel() + "': opción inválida '" + v + "'");
        });
    }

    private void validateFile(FormField f, Object value, List<String> errors) {
        if (value.toString().isBlank())
            errors.add("'" + f.getLabel() + "': archivo no adjuntado");
    }

    private void validateSignature(FormField f, Object value, List<String> errors) {
        String sig = value.toString();
        if (!sig.startsWith("data:image/") && !sig.startsWith("http"))
            errors.add("'" + f.getLabel() + "': firma no válida");
    }

    @SuppressWarnings("unchecked")
    private void validateGeolocation(FormField f, Object value, List<String> errors) {
        if (!(value instanceof Map)) {
            errors.add("'" + f.getLabel() + "' debe tener {lat, lng}");
            return;
        }
        Map<String, Object> geo = (Map<String, Object>) value;
        if (!geo.containsKey("lat") || !geo.containsKey("lng"))
            errors.add("'" + f.getLabel() + "': faltan lat y/o lng");
    }

    private boolean isEmpty(Object value) {
        return value == null || value.toString().isBlank();
    }
}