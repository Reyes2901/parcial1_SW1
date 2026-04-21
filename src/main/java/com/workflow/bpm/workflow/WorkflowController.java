package com.workflow.bpm.workflow;

import com.workflow.bpm.task.TaskService;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.dto.StartProcessRequest;
import com.workflow.bpm.workflow.engine.WorkflowEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine engine;
    private final WorkflowService workflowService;
    private final TaskService taskService;

    // --- Iniciar trámite ---
    @PostMapping("/start")
    public ResponseEntity<ProcessInstance> start(
            @Valid @RequestBody StartProcessRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(201).body(
                engine.startProcess(
                        req.getDefinitionId(),
                        user.getUsername(),
                        req.getClientName(),
                        req.getClientData()
                )
        );
    }

    // --- Ver estado del trámite ---
    @GetMapping("/instances/{id}")
    public ResponseEntity<ProcessInstance> getInstance(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.getInstance(id));
    }

    // --- Historial de trámites del cliente ---
    @GetMapping("/instances/my-requests")
    public ResponseEntity<List<ProcessInstance>> myRequests(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(workflowService.getByClient(user.getUsername()));
    }

    // --- Bandeja del funcionario ---
    @GetMapping("/tasks/my-tasks")
    public ResponseEntity<List<TaskInstance>> myTasks(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(workflowService.getMyTasks(user.getUsername()));
    }

    // --- Funcionario abre la tarea ---
    @PostMapping("/tasks/{id}/start")
    public ResponseEntity<TaskInstance> startTask(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(taskService.startTask(id, user.getUsername()));
    }

    // --- Funcionario completa la tarea con datos del formulario ---
    @PostMapping("/tasks/{id}/complete")
    public ResponseEntity<TaskInstance> completeTask(
            @PathVariable String id,
            @RequestBody Map<String, Object> formData,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                taskService.completeTask(id, user.getUsername(), formData)
        );
    }

    // --- Funcionario rechaza la tarea ---
    @PostMapping("/tasks/{id}/reject")
    public ResponseEntity<TaskInstance> rejectTask(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                taskService.rejectTask(id, user.getUsername(), body.get("motivo"))
        );
    }
    
    // --- Cancelar instancia (admin) ---
    @PostMapping("/instances/{id}/cancel")
    public ResponseEntity<ProcessInstance> cancelInstance(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(engine.cancelInstance(id, body.get("reason")));
    }
}