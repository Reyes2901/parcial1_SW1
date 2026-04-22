package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.notification.NotificationService;
import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Lane;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.user.User;
import com.workflow.bpm.user.UserRepository;
import com.workflow.bpm.workflow.document.ProcessInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import javax.management.Notification;

@Component("ACTIVITY")
@RequiredArgsConstructor
@Slf4j
public class ActivityNodeExecutor implements NodeExecutor {

    private final TaskInstanceRepository taskRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition, 
                                Node node) {
        
        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeLabel(node.getLabel());
        
        // ELIMINADO: instance.addAuditEntry(...)

        String assigneeId = resolveAssignee(node, definition);

        int estimatedHours = node.getEstimatedDurationHours() != null 
                ? node.getEstimatedDurationHours() : 24;

        TaskInstance task = TaskInstance.builder()
                .instanceId(instance.getId())
                .definitionId(instance.getDefinitionId())
                .nodeId(node.getId())
                .nodeLabel(node.getLabel())
                .laneId(node.getLaneId())
                .assigneeId(assigneeId)
                .assigneeRole(node.getAssigneeRole())
                .status(TaskInstance.STATUS_PENDING)
                .priority(TaskInstance.PRIORITY_NORMAL)
                .formSchema(node.getFormSchema())
                .clientName(instance.getClientName())
                .createdAt(Instant.now())
                .dueAt(Instant.now().plus(estimatedHours, ChronoUnit.HOURS))
                .build();
        
        TaskInstance savedTask = taskRepo.save(task);
        notificationService.notifyNewTask(assigneeId, savedTask);
        log.info("[ACTIVITY] Tarea '{}' creada para '{}' (due: {})", 
                 node.getLabel(), assigneeId, task.getDueAt());

        return Collections.emptyList();
    }

    private String resolveAssignee(Node node, ProcessDefinition definition) {
        Lane lane = definition.getLanes().stream()
                .filter(l -> l.getId().equals(node.getLaneId()))
                .findFirst()
                .orElse(null);

        if (lane != null && node.getAssigneeRole() != null) {
        // 👇 Usar el nuevo método con departmentId
        return userRepo.findFirstByRoleAndDepartmentId(
                        node.getAssigneeRole(), 
                        lane.getDepartmentId())
                .map(User::getUsername)
                .orElseGet(() -> userRepo.findByRole(node.getAssigneeRole()).stream()
                        .findFirst()
                        .map(User::getUsername)
                        .orElse(node.getAssigneeRole()));
        }
        
        return node.getAssigneeRole() != null ? node.getAssigneeRole() : "unassigned";
    }
}