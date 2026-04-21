package com.workflow.bpm.task;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.user.User;
import com.workflow.bpm.user.UserRepository;
import com.workflow.bpm.workflow.document.AuditEntry;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import com.workflow.bpm.workflow.engine.WorkflowEngine;
import com.workflow.bpm.workflow.engine.WorkflowException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskInstanceRepository taskRepo;
    private final ProcessInstanceRepository instanceRepo;
    private final UserRepository userRepo;
    private final WorkflowEngine engine;

    /**
     * Verifica si el usuario tiene permiso para trabajar la tarea
     */
    private void checkAccess(TaskInstance task, String username) {
        // 1. Verificar por assigneeId directo
        if (username.equals(task.getAssigneeId())) {
            return;
        }
        
        // 2. Verificar por rol
        User user = userRepo.findByUsername(username).orElse(null);
        if (user != null && user.getRole() != null && user.getRole().equals(task.getAssigneeRole())) {
            return;
        }
        
        throw new AccessDeniedException("No eres el responsable de esta tarea");
    }

    public TaskInstance completeTask(String taskId,
                                     String userId,
                                     Map<String, Object> formData) {

        TaskInstance task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // Validar acceso
        checkAccess(task, userId);

        if (List.of(TaskInstance.STATUS_COMPLETED, TaskInstance.STATUS_REJECTED).contains(task.getStatus())) {
            throw new WorkflowException("La tarea ya fue procesada");
        }

        task.setFormSubmission(formData);
        task.setStatus(TaskInstance.STATUS_COMPLETED);
        task.setCompletedAt(Instant.now());
        if (task.getStartedAt() != null) {
            task.setDurationMinutes(
                    ChronoUnit.MINUTES.between(task.getStartedAt(), task.getCompletedAt()));
        }
        taskRepo.save(task);

        ProcessInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada"));
        
        if (formData != null) {
            // Solo agregar las variables del formulario, no todo el objeto
            formData.forEach((key, value) -> {
                // Evitar sobrescribir variables del sistema
                if (!key.startsWith("_") && !List.of("definitionId", "clientName", "clientData").contains(key)) {
                    instance.getVariables().put(key, value);
                }
            });
        }

        instance.getAuditLog().add(AuditEntry.builder()
                .nodeId(task.getNodeId())
                .nodeLabel(task.getNodeLabel())
                .action(AuditEntry.ACTION_NODE_COMPLETED)
                .userId(userId)
                .timestamp(Instant.now())
                .durationMinutes(task.getDurationMinutes())
                .formData(formData)
                .build());
        instanceRepo.save(instance);

        engine.resumeAfterTask(instance.getId(), task.getNodeId(), formData);

        log.info("✅ Tarea '{}' completada por {}. Motor reanudado.", task.getNodeLabel(), userId);
        return task;
    }

    public TaskInstance startTask(String taskId, String userId) {
        TaskInstance task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Validar acceso
        checkAccess(task, userId);
        
        if (!TaskInstance.STATUS_PENDING.equals(task.getStatus())) {
            throw new WorkflowException("La tarea no está en estado PENDING");
        }

        task.setStatus(TaskInstance.STATUS_IN_PROGRESS);
        task.setStartedAt(Instant.now());
        
        log.info("▶ Tarea '{}' iniciada por {}", task.getNodeLabel(), userId);
        return taskRepo.save(task);
    }

    public TaskInstance rejectTask(String taskId, String userId, String motivo) {
        TaskInstance task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Validar acceso
        checkAccess(task, userId);

        task.setStatus(TaskInstance.STATUS_REJECTED);
        task.setCompletedAt(Instant.now());
        Map<String, Object> data = Map.of("motivoRechazo", motivo != null ? motivo : "No especificado");
        task.setFormSubmission(data);
        taskRepo.save(task);

        ProcessInstance inst = instanceRepo.findById(task.getInstanceId())
                .orElseThrow();
        inst.setStatus(ProcessInstance.STATUS_REJECTED);
        inst.setCompletedAt(Instant.now());
        inst.getVariables().put("motivoRechazo", motivo);
        
        inst.getAuditLog().add(AuditEntry.builder()
                .nodeId(task.getNodeId())
                .nodeLabel(task.getNodeLabel())
                .action(AuditEntry.ACTION_REJECTED)
                .userId(userId)
                .timestamp(Instant.now())
                .formData(Map.of("motivo", motivo))
                .build());
        
        instanceRepo.save(inst);

        log.info("❌ Tarea '{}' rechazada por {}. Motivo: {}", task.getNodeLabel(), userId, motivo);
        return task;
    }

    public List<TaskInstance> getTasksByAssigneeAndStatus(String assigneeId, List<String> statuses) {
        return taskRepo.findByAssigneeIdAndStatusIn(assigneeId, statuses);
    }

    public List<TaskInstance> getTasksByInstance(String instanceId) {
        return taskRepo.findByInstanceId(instanceId);
    }
}