package com.workflow.bpm.workflow;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import com.workflow.bpm.workflow.engine.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final ProcessInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskRepo;
    private final WorkflowEngine engine;

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