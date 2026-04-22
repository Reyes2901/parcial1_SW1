package com.workflow.bpm.form;

import com.workflow.bpm.form.document.FormSubmission;
import com.workflow.bpm.form.document.FormSubmissionRepository;
import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.shared.model.FormField;
import com.workflow.bpm.shared.model.FormSchema;
import com.workflow.bpm.task.TaskService;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormService {

    private final TaskInstanceRepository taskRepo;
    private final FormSubmissionRepository submissionRepo;  // 👈 Nuevo
    private final FormValidationService validator;
    private final TaskService taskService;

    /**
     * Flujo completo de envío de formulario
     */
    public TaskInstance submitForm(String taskId,
                                   String userId,
                                   Map<String, Object> formData) {

        TaskInstance task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // 1. Validar contra el formSchema
        if (task.getFormSchema() != null) {
            validator.validate(task.getFormSchema(), formData);
        }

        // 2. Normalizar tipos
        Map<String, Object> normalizedData = normalizeFormData(task.getFormSchema(), formData);

        // 3. Guardar FormSubmission (colección separada)
        List<String> attachmentUrls = extractUrls(normalizedData);
        String signatureUrl = extractSignatureUrl(normalizedData);

        FormSubmission submission = FormSubmission.builder()
                .taskId(taskId)
                .instanceId(task.getInstanceId())
                .nodeId(task.getNodeId())
                .submittedBy(userId)
                .data(normalizedData)
                .attachmentUrls(attachmentUrls)
                .signatureUrl(signatureUrl)
                .submittedAt(Instant.now())
                .build();

        FormSubmission saved = submissionRepo.save(submission);

        // 4. Guardar referencia en TaskInstance
        task.setFormSubmissionId(saved.getId());
        taskRepo.save(task);

        // 5. Completar tarea y reanudar motor
        log.info("✅ Formulario enviado para tarea '{}'", task.getNodeLabel());
        return taskService.completeTask(taskId, userId, normalizedData);
    }

    /**
     * Guardar borrador
     */
    public TaskInstance saveDraft(String taskId,
                                  String userId,
                                  Map<String, Object> partialData) {
        TaskInstance task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!userId.equals(task.getAssigneeId()))
            throw new AccessDeniedException("No eres el responsable");

        task.setFormSubmission(partialData);
        if (!TaskInstance.STATUS_IN_PROGRESS.equals(task.getStatus())) {
            task.setStatus(TaskInstance.STATUS_IN_PROGRESS);
            task.setStartedAt(Instant.now());
        }
        log.info("📝 Borrador guardado para tarea '{}'", task.getNodeLabel());
        return taskRepo.save(task);
    }

    private Map<String, Object> normalizeFormData(FormSchema schema, Map<String, Object> raw) {
        if (schema == null || schema.getFields() == null) return new HashMap<>(raw);
        Map<String, Object> result = new HashMap<>(raw);

        schema.getFields().forEach(field -> {
            Object value = raw.get(field.getName());
            if (value == null) return;

            Object normalized = switch (field.getType()) {
                case FormField.TYPE_BOOLEAN -> Boolean.parseBoolean(value.toString());
                case FormField.TYPE_NUMBER -> {
                    try {
                        String valStr = value.toString();
                        yield valStr.contains(".") ? Double.parseDouble(valStr) : Long.parseLong(valStr);
                    } catch (NumberFormatException e) {
                        yield value;
                    }
                }
                default -> value;
            };
            result.put(field.getName(), normalized);
        });
        return result;
    }

    private List<String> extractUrls(Map<String, Object> data) {
        return data.values().stream()
                .filter(v -> v instanceof String && ((String) v).startsWith("/api/files/"))
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private String extractSignatureUrl(Map<String, Object> data) {
        return data.values().stream()
                .filter(v -> v instanceof String && ((String) v).contains("/signatures/"))
                .map(Object::toString)
                .findFirst()
                .orElse(null);
    }
}