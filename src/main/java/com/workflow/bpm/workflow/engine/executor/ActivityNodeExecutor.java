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

/**
 * Handles both ACTIVITY and TASK node types (TASK is a frontend-friendly alias).
 *
 * Parallel awareness: if a FORK node has previously stored a parallelGroupId in
 * instance variables (key: "_parallelGroupId_<forkNodeId>"), this executor reads
 * it and stamps the created TaskInstance with that group ID so the JOIN can wait
 * for all siblings.
 */
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

        String assigneeId = resolveAssignee(node, definition);
        String departmentId = resolveDepartmentId(node, definition);

        int estimatedHours = node.getEstimatedDurationHours() != null
                ? node.getEstimatedDurationHours() : 24;

        // ── Parallel group wiring ─────────────────────────────────────
        String parallelGroupId = resolveParallelGroupId(instance, definition, node);

        TaskInstance task = TaskInstance.builder()
                .instanceId(instance.getId())
                .definitionId(instance.getDefinitionId())
                .nodeId(node.getId())
                .nodeLabel(node.getLabel())
                .laneId(node.getLaneId())
                .departmentId(departmentId)
                .assigneeId(assigneeId)
                .assignedUserId(assigneeId)
                .assigneeRole(node.getAssigneeRole())
                .parallelGroupId(parallelGroupId)
                .status(TaskInstance.STATUS_PENDING)
                .priority(TaskInstance.PRIORITY_NORMAL)
                .formSchema(node.getFormSchema())
                .clientName(instance.getClientName())
                .createdAt(Instant.now())
                .dueAt(Instant.now().plus(estimatedHours, ChronoUnit.HOURS))
                .build();

        TaskInstance savedTask = taskRepo.save(task);
        notificationService.notifyNewTask(assigneeId, savedTask);
        log.info("[ACTIVITY] Tarea '{}' creada para '{}' (due: {}, groupId: {})",
                node.getLabel(), assigneeId, task.getDueAt(), parallelGroupId);

        // Returning empty list pauses the engine — a human must complete the task
        return Collections.emptyList();
    }

    // ── Private helpers ───────────────────────────────────────────────

    /**
     * Reads the parallelGroupId that ForkNodeExecutor stored in instance.variables.
     * Key format: "_parallelGroupId_<forkNodeId>"
     * We find the FORK node that has a transition pointing at this node.
     */
    private String resolveParallelGroupId(ProcessInstance instance,
                                          ProcessDefinition definition,
                                          Node node) {
        return definition.getTransitions().stream()
                .filter(t -> t.getTargetId().equals(node.getId()))
                .map(t -> definition.findNodeByIdSafe(t.getSourceId()).orElse(null))
                .filter(src -> src != null && Node.TYPE_FORK.equals(src.getType()))
                .map(forkNode -> {
                    String key = "_parallelGroupId_" + forkNode.getId();
                    Object val = instance.getVariables().get(key);
                    return val != null ? val.toString() : null;
                })
                .filter(gid -> gid != null)
                .findFirst()
                .orElse(null);
    }

    private String resolveDepartmentId(Node node, ProcessDefinition definition) {
        if (node.getLaneId() == null) return null;
        return definition.getLanes().stream()
                .filter(l -> l.getId().equals(node.getLaneId()))
                .findFirst()
                .map(Lane::getDepartmentId)
                .orElse(null);
    }

    private String resolveAssignee(Node node, ProcessDefinition definition) {
        Lane lane = definition.getLanes().stream()
                .filter(l -> l.getId().equals(node.getLaneId()))
                .findFirst()
                .orElse(null);

        if (lane != null && node.getAssigneeRole() != null) {
            // 1. User with that ROLE in that DEPARTMENT
            return userRepo.findFirstByRoleAndDepartmentId(
                            node.getAssigneeRole(),
                            lane.getDepartmentId())
                    .map(User::getUsername)
                    // 2. Any user with that ROLE
                    .orElseGet(() -> userRepo.findByRole(node.getAssigneeRole()).stream()
                            .findFirst()
                            .map(User::getUsername)
                            // 3. Admin fallback
                            .orElse("admin"));
        }

        if (node.getAssigneeRole() != null) {
            return userRepo.findByRole(node.getAssigneeRole()).stream()
                    .findFirst()
                    .map(User::getUsername)
                    .orElse("admin");
        }

        return "admin";
    }
}