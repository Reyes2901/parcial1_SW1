package com.workflow.bpm.workflow;

import com.workflow.bpm.analytics.BottleneckDetector;
import com.workflow.bpm.task.TaskService;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.dto.InstanceOverviewItem;
import com.workflow.bpm.workflow.dto.ProcessHistoryResponse;
import com.workflow.bpm.workflow.dto.StartProcessRequest;
import com.workflow.bpm.workflow.engine.WorkflowEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow", description = "Workflow engine endpoints")
public class WorkflowController {

    private final WorkflowEngine engine;
    private final WorkflowService workflowService;
    private final OverviewService overviewService;
    private final TaskService taskService;
    private final BottleneckDetector bottleneckDetector;
    private final SimpMessagingTemplate template;

    // --- Iniciar trámite ---
    @PostMapping("/start")
    @Operation(summary = "Start a new process instance")
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

    // --- Admin: Vista global paginada de TODAS las instancias ---
    @GetMapping("/instances")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: paginated list of all process instances with optional filters")
    public ResponseEntity<Page<ProcessInstance>> getAllInstances(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(workflowService.getAllInstances(status, policyId, pageable));
    }

    // --- Ver estado de un trámite específico ---
    @GetMapping("/instances/{id}")
    @Operation(summary = "Get a single process instance by ID")
    public ResponseEntity<ProcessInstance> getInstance(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.getInstance(id));
    }

    // --- Historial de trámites del cliente (con filtro opcional por status) ---
    @GetMapping("/instances/my-requests")
    @Operation(summary = "Client: list own process instances with optional status filter")
    public ResponseEntity<List<ProcessInstance>> myRequests(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(workflowService.getByClientWithFilter(user.getUsername(), status));
    }

    // --- Admin: Seguimiento agrupado por departamento ---
    @GetMapping("/instances/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: all process instances grouped by department with tasks")
    public ResponseEntity<List<InstanceOverviewItem>> getOverview(
            @Parameter(description = "Filter by instance status (IN_PROGRESS, COMPLETED, etc.)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by department ID")
            @RequestParam(required = false) String departmentId) {
        return ResponseEntity.ok(overviewService.getOverview(status, departmentId, null));
    }

    // --- Cliente: sus trámites agrupados por departamento ---
    @GetMapping("/instances/my-requests/overview")
    @Operation(summary = "Client: own process instances grouped by department with tasks")
    public ResponseEntity<List<InstanceOverviewItem>> getMyRequestsOverview(
            @AuthenticationPrincipal UserDetails user,
            @Parameter(description = "Filter by instance status")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by department ID")
            @RequestParam(required = false) String departmentId) {
        return ResponseEntity.ok(overviewService.getOverview(status, departmentId, user.getUsername()));
    }

    // --- Historial completo de un trámite ---
    @GetMapping("/instances/{id}/history")
    @Operation(summary = "Get full audit history of a process instance")
    public ResponseEntity<ProcessHistoryResponse> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.getHistory(id));
    }

    // --- Bandeja del funcionario ---
    @GetMapping("/tasks/my-tasks")
    @Operation(summary = "Funcionario: list own pending/in-progress tasks")
    public ResponseEntity<List<TaskInstance>> myTasks(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(workflowService.getMyTasks(user.getUsername()));
    }

    // --- Funcionario abre la tarea ---
    @PostMapping("/tasks/{id}/start")
    @Operation(summary = "Start a task (mark as IN_PROGRESS)")
    public ResponseEntity<TaskInstance> startTask(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(taskService.startTask(id, user.getUsername()));
    }

    // --- Funcionario completa la tarea con datos del formulario ---
    @PostMapping("/tasks/{id}/complete")
    @Operation(summary = "Complete a task with form data")
    public ResponseEntity<TaskInstance> completeTask(
            @PathVariable String id,
            @RequestBody Map<String, Object> formData,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(taskService.completeTask(id, user.getUsername(), formData));
    }

    // --- Funcionario rechaza la tarea ---
    @PostMapping("/tasks/{id}/reject")
    @Operation(summary = "Reject a task with a reason")
    public ResponseEntity<TaskInstance> rejectTask(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(taskService.rejectTask(id, user.getUsername(), body.get("motivo")));
    }

    // --- Cancelar instancia (admin) ---
    @PostMapping("/instances/{id}/cancel")
    @Operation(summary = "Cancel a process instance")
    public ResponseEntity<ProcessInstance> cancelInstance(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(engine.cancelInstance(id, body.get("reason")));
    }

    // --- WebSocket smoke test ---
    @GetMapping("/test/ws")
    @Operation(summary = "WebSocket smoke test")
    public String test() {
        template.convertAndSend("/topic/test", "🔥 hola desde backend");
        return "ok";
    }

    // --- Admin: disparo manual del detector de cuellos de botella ---
    @PostMapping("/admin/trigger-bottleneck-check")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: manually trigger bottleneck detection")
    public ResponseEntity<String> triggerBottleneck() {
        bottleneckDetector.detectarCuellos();
        return ResponseEntity.ok("Bottleneck check ejecutado manualmente");
    }

    // --- Admin: estadísticas rápidas ---
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: quick workflow statistics")
    public ResponseEntity<WorkflowService.WorkflowStats> getStats() {
        return ResponseEntity.ok(workflowService.getStats());
    }
}