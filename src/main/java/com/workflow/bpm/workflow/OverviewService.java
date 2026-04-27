package com.workflow.bpm.workflow;

import com.workflow.bpm.department.Department;
import com.workflow.bpm.department.DepartmentRepository;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import com.workflow.bpm.workflow.dto.DepartmentTaskGroup;
import com.workflow.bpm.workflow.dto.InstanceOverviewItem;
import com.workflow.bpm.workflow.dto.TaskOverviewItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the grouped overview response for GET /api/workflow/instances/overview.
 *
 * Performance design:
 *  1. Fetch all matching ProcessInstances in ONE query.
 *  2. Collect all their IDs, fetch ALL TaskInstances in ONE query.
 *  3. Build a departmentId → departmentName map from a SINGLE DepartmentRepository call.
 *  4. Group and map in-memory — zero N+1 queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OverviewService {

    private final ProcessInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskRepo;
    private final DepartmentRepository departmentRepo;

    /**
     * Returns all instances (optionally filtered) with tasks grouped by department.
     *
     * @param status       optional status filter (IN_PROGRESS, COMPLETED, ...)
     * @param departmentId optional — only instances that have tasks in this department
     * @param userId       optional — only instances owned by (clientId == userId)
     */
    public List<InstanceOverviewItem> getOverview(String status,
                                                   String departmentId,
                                                   String userId) {

        // ── Step 1: Fetch instances (one query) ──────────────────────
        List<ProcessInstance> instances = fetchInstances(status, userId);
        if (instances.isEmpty()) return List.of();

        // ── Step 2: Batch fetch all tasks (one query) ────────────────
        Set<String> instanceIds = instances.stream()
                .map(ProcessInstance::getId)
                .collect(Collectors.toSet());

        List<TaskInstance> allTasks = taskRepo.findByInstanceIdIn(new ArrayList<>(instanceIds));

        // ── Step 3: Group tasks by instanceId (in-memory) ────────────
        Map<String, List<TaskInstance>> tasksByInstance = allTasks.stream()
                .collect(Collectors.groupingBy(TaskInstance::getInstanceId));

        // ── Step 4: Build department name map (one query) ─────────────
        Set<String> deptIds = allTasks.stream()
                .filter(t -> t.getDepartmentId() != null)
                .map(TaskInstance::getDepartmentId)
                .collect(Collectors.toSet());

        Map<String, String> deptNames = departmentRepo.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));

        // ── Step 5: Build response (all in-memory) ───────────────────
        List<InstanceOverviewItem> result = new ArrayList<>();

        for (ProcessInstance inst : instances) {
            List<TaskInstance> tasks = tasksByInstance.getOrDefault(inst.getId(), List.of());

            // Apply departmentId filter at instance level
            if (departmentId != null && !departmentId.isBlank()) {
                boolean hasTaskInDept = tasks.stream()
                        .anyMatch(t -> departmentId.equals(t.getDepartmentId()));
                if (!hasTaskInDept) continue;
            }

            List<DepartmentTaskGroup> deptGroups = buildDepartmentGroups(tasks, deptNames, departmentId);

            result.add(InstanceOverviewItem.builder()
                    .instanceId(inst.getId())
                    .policyId(inst.getDefinitionId())
                    .policyName(inst.getDefinitionName())
                    .processTypeId(null)   // populated if ProcessDefinition carries processTypeId
                    .status(inst.getStatus())
                    .clientId(inst.getClientId())
                    .clientName(inst.getClientName())
                    .currentNodeLabel(inst.getCurrentNodeLabel())
                    .createdAt(inst.getCreatedAt())
                    .startedAt(inst.getStartedAt())
                    .completedAt(inst.getCompletedAt())
                    .departments(deptGroups)
                    .build());
        }

        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────

    private List<ProcessInstance> fetchInstances(String status, String userId) {
        if (userId != null && !userId.isBlank() && status != null && !status.isBlank()) {
            return instanceRepo.findByClientIdAndStatus(userId, status);
        } else if (userId != null && !userId.isBlank()) {
            return instanceRepo.findByClientId(userId);
        } else if (status != null && !status.isBlank()) {
            return instanceRepo.findByStatus(status);
        }
        return instanceRepo.findAll();
    }

    private List<DepartmentTaskGroup> buildDepartmentGroups(List<TaskInstance> tasks,
                                                             Map<String, String> deptNames,
                                                             String filterDeptId) {
        // Group tasks by departmentId (null → "unassigned")
        Map<String, List<TaskInstance>> byDept = tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getDepartmentId() != null ? t.getDepartmentId() : "unassigned"));

        List<DepartmentTaskGroup> groups = new ArrayList<>();

        byDept.forEach((dId, dTasks) -> {
            // Apply department filter if provided
            if (filterDeptId != null && !filterDeptId.isBlank() && !filterDeptId.equals(dId)) {
                return;
            }

            List<TaskOverviewItem> items = dTasks.stream()
                    .map(this::toTaskItem)
                    .collect(Collectors.toList());

            groups.add(DepartmentTaskGroup.builder()
                    .departmentId(dId)
                    .departmentName(deptNames.getOrDefault(dId, dId))
                    .tasks(items)
                    .build());
        });

        return groups;
    }

    private TaskOverviewItem toTaskItem(TaskInstance t) {
        return TaskOverviewItem.builder()
                .taskId(t.getId())
                .nodeId(t.getNodeId())
                .name(t.getNodeLabel())
                .status(t.getStatus())
                .assignedUserId(t.getAssignedUserId() != null ? t.getAssignedUserId() : t.getAssigneeId())
                .assigneeRole(t.getAssigneeRole())
                .parallelGroupId(t.getParallelGroupId())
                .priority(t.getPriority())
                .dueAt(t.getDueAt())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}
