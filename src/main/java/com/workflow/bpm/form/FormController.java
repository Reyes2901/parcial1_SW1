package com.workflow.bpm.form;

import com.workflow.bpm.form.dto.FormSchemaResponse;
import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Forms", description = "Task form schema and submission endpoints")
public class FormController {

    private final TaskInstanceRepository taskRepo;
    private final FormService formService;

    /**
     * Devuelve el formSchema de la tarea.
     * Angular lo usa para renderizar el formulario dinámicamente.
     */
    @GetMapping("/{id}/form")
    @Operation(summary = "Get the form schema for a task")
    public ResponseEntity<FormSchemaResponse> getForm(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {

        TaskInstance task = taskRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!user.getUsername().equals(task.getAssigneeId())) {
            throw new AccessDeniedException("No tienes acceso a esta tarea");
        }

        return ResponseEntity.ok(FormSchemaResponse.builder()
                .taskId(task.getId())
                .nodeLabel(task.getNodeLabel())
                .clientName(task.getClientName())
                .status(task.getStatus())
                .dueAt(task.getDueAt())
                .schema(task.getFormSchema())
                .existingSubmission(task.getFormSubmission())
                .build());
    }

    /**
     * El funcionario envía los datos del formulario.
     * Valida, guarda y reanuda el motor de workflow.
     */
    @PostMapping("/{id}/form")
    @Operation(summary = "Submit form data for a task")
    public ResponseEntity<TaskInstance> submitForm(
            @PathVariable String id,
            @RequestBody Map<String, Object> formData,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                formService.submitForm(id, user.getUsername(), formData));
    }

    /**
     * Guardar progreso sin completar.
     */
    @PutMapping("/{id}/form/save-draft")
    @Operation(summary = "Save form draft without completing task")
    public ResponseEntity<TaskInstance> saveDraft(
            @PathVariable String id,
            @RequestBody Map<String, Object> partialData,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                formService.saveDraft(id, user.getUsername(), partialData));
    }
}