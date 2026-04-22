package com.workflow.bpm.workflow;

import com.workflow.bpm.form.document.FormSubmission;
import com.workflow.bpm.form.document.FormSubmissionRepository;
import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import com.workflow.bpm.workflow.dto.ProcessHistoryResponse;
import com.workflow.bpm.workflow.dto.TaskSummary;
import com.workflow.bpm.workflow.engine.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final ProcessInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskRepo;
    private final WorkflowEngine engine;
    private final FormSubmissionRepository formSubmissionRepo;  // 👈 Agregar

    /**
     * Obtener una instancia por su ID
     */
    public ProcessInstance getInstance(String instanceId) {
        return instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));
    }

    /**
     * Bandeja del funcionario — ordenada por urgencia (dueAt más próximo primero)
     */
    public List<TaskInstance> getMyTasks(String userId) {
        return taskRepo
                .findByAssigneeIdAndStatusIn(userId, 
                        List.of(TaskInstance.STATUS_PENDING, TaskInstance.STATUS_IN_PROGRESS))
                .stream()
                .sorted(Comparator.comparing(
                        TaskInstance::getDueAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Trámites del cliente — historial de sus solicitudes
     */
    public List<ProcessInstance> getByClient(String clientId) {
        return instanceRepo.findByClientId(clientId);
    }

    /**
     * Vista admin — todos los trámites activos en este momento
     */
    public List<ProcessInstance> getAllActive() {
        return instanceRepo.findByStatus(ProcessInstance.STATUS_IN_PROGRESS);
    }

    /**
     * Todas las tareas de un trámite — detalle para el admin
     */
    public List<TaskInstance> getTasksByInstance(String instanceId) {
        return taskRepo.findByInstanceId(instanceId);
    }

    /**
     * Obtener tareas por estado
     */
    public List<TaskInstance> getTasksByStatus(String status) {
        return taskRepo.findByStatus(status);
    }

    /**
     * Obtener instancias por definición y estado
     */
    public List<ProcessInstance> getInstancesByDefinitionAndStatus(String definitionId, String status) {
        return instanceRepo.findByDefinitionIdAndStatus(definitionId, status);
    }

    /**
     * Cancelar instancia (admin)
     */
    public ProcessInstance cancelInstance(String instanceId, String reason) {
        return engine.cancelInstance(instanceId, reason);
    }

    /**
     * Obtener estadísticas básicas para dashboard
     */
    public WorkflowStats getStats() {
        long activeInstances = instanceRepo.countByStatus(ProcessInstance.STATUS_IN_PROGRESS);
        long completedInstances = instanceRepo.countByStatus(ProcessInstance.STATUS_COMPLETED);
        long pendingTasks = taskRepo.countByStatus(TaskInstance.STATUS_PENDING);
        long inProgressTasks = taskRepo.countByStatus(TaskInstance.STATUS_IN_PROGRESS);
        
        return WorkflowStats.builder()
                .activeInstances(activeInstances)
                .completedInstances(completedInstances)
                .pendingTasks(pendingTasks)
                .inProgressTasks(inProgressTasks)
                .build();
    }
    public ProcessHistoryResponse getHistory(String instanceId) {
        ProcessInstance instance = getInstance(instanceId);
        List<TaskInstance> tasks = taskRepo.findByInstanceId(instanceId);

        List<TaskSummary> summaries = tasks.stream()
                .map(t -> {
                    Map<String, Object> formData = t.getFormSubmission();
                    
                    // Si hay FormSubmission separado, usarlo
                    if (t.getFormSubmissionId() != null) {
                        formData = formSubmissionRepo.findById(t.getFormSubmissionId())
                                .map(FormSubmission::getData)
                                .orElse(t.getFormSubmission());
                    }
                    
                    return TaskSummary.builder()
                            .nodeLabel(t.getNodeLabel())
                            .status(t.getStatus())
                            .assigneeId(t.getAssigneeId())
                            .durationMinutes(t.getDurationMinutes())
                            .completedAt(t.getCompletedAt())
                            .formData(formData)
                            .build();
                })
                .collect(Collectors.toList());

        return ProcessHistoryResponse.builder()
                .instanceId(instance.getId())
                .definitionName(instance.getDefinitionName())
                .status(instance.getStatus())
                .clientName(instance.getClientName())
                .startedAt(instance.getStartedAt())
                .completedAt(instance.getCompletedAt())
                .progressPct(calcularProgreso(instance))
                .auditLog(instance.getAuditLog())
                .tasks(summaries)
                .build();
    }

    private int calcularProgreso(ProcessInstance instance) {
        if ("COMPLETED".equals(instance.getStatus())) return 100;
        if ("REJECTED".equals(instance.getStatus())) return 0;
        long completadas = instance.getAuditLog().stream()
                .filter(a -> "NODE_COMPLETED".equals(a.getAction()))
                .count();
        return (int) Math.min(90, completadas * 20);
    }

    // DTO interno para estadísticas
    @lombok.Data
    @lombok.Builder
    public static class WorkflowStats {
        private long activeInstances;
        private long completedInstances;
        private long pendingTasks;
        private long inProgressTasks;
    }
}