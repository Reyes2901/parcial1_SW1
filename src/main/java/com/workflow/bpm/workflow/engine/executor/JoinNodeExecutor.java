package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.shared.model.Transition;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.workflow.document.ProcessInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JOIN executor — parallel synchronization barrier.
 *
 * Synchronization strategy:
 *   1. Find the FORK node that owns the incoming parallel branches by walking backwards
 *      through the transition graph from this JOIN node.
 *   2. Read the parallelGroupId that ForkNodeExecutor stored in instance variables.
 *   3. Count the total tasks in that group vs. how many are COMPLETED.
 *   4. If all are done → continue; otherwise → halt and wait.
 *
 * Fallback (no parallelGroupId found, i.e. legacy sequential processes):
 *   Uses the old strategy of counting incoming transitions.
 */
@Component("JOIN")
@RequiredArgsConstructor
@Slf4j
public class JoinNodeExecutor implements NodeExecutor {

    private final TaskInstanceRepository taskRepo;

    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition,
                                Node node) {

        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeLabel(node.getLabel());

        // ── Step 1: locate the FORK that feeds this JOIN ─────────────
        String forkNodeId = findOriginForkNodeId(definition, node.getId());
        String groupId = forkNodeId != null
                ? (String) instance.getVariables().get("_parallelGroupId_" + forkNodeId)
                : null;

        // ── Step 2: check synchronization ────────────────────────────
        if (groupId != null) {
            return checkByParallelGroup(instance, definition, node, groupId, forkNodeId);
        } else {
            return checkByIncomingCount(instance, definition, node);
        }
    }

    // ── Parallel-group strategy (preferred) ──────────────────────────

    private List<String> checkByParallelGroup(ProcessInstance instance,
                                              ProcessDefinition definition,
                                              Node node,
                                              String groupId,
                                              String forkNodeId) {

        long total     = taskRepo.countByInstanceIdAndParallelGroupId(instance.getId(), groupId);
        long completed = taskRepo.countByInstanceIdAndParallelGroupIdAndStatus(
                             instance.getId(), groupId, TaskInstance.STATUS_COMPLETED);

        log.info("[JOIN] '{}': {}/{} parallel tasks completed (groupId={})",
                node.getLabel(), completed, total, groupId);

        if (completed < total) {
            // Not all siblings done yet — pause the engine
            return Collections.emptyList();
        }

        // All branches done — clean up and proceed
        log.info("[JOIN] '{}': all parallel branches complete. Advancing.", node.getLabel());
        instance.getVariables().remove("_parallelGroupId_" + forkNodeId);
        instance.getVariables().remove("_activeForkNodeId");

        return outgoingTargets(definition, node);
    }

    // ── Fallback: count incoming transitions (legacy / no fork found) ─

    private List<String> checkByIncomingCount(ProcessInstance instance,
                                              ProcessDefinition definition,
                                              Node node) {

        long totalIncoming = definition.getTransitions().stream()
                .filter(t -> t.getTargetId().equals(node.getId()))
                .count();

        long completed = taskRepo.findByInstanceId(instance.getId()).stream()
                .filter(t -> TaskInstance.STATUS_COMPLETED.equals(t.getStatus()))
                .count();

        log.info("[JOIN] '{}' (fallback): {}/{} tasks completed.",
                node.getLabel(), completed, totalIncoming);

        if (completed < totalIncoming) {
            return Collections.emptyList();
        }

        log.info("[JOIN] '{}': all incoming satisfied. Advancing.", node.getLabel());
        return outgoingTargets(definition, node);
    }

    // ── Graph traversal: find the FORK that originates this JOIN ─────

    private String findOriginForkNodeId(ProcessDefinition definition, String joinNodeId) {
        // Walk the incoming transitions of the JOIN's predecessors backwards
        // to locate the nearest FORK ancestor.
        List<String> directParentIds = definition.getTransitions().stream()
                .filter(t -> t.getTargetId().equals(joinNodeId))
                .map(Transition::getSourceId)
                .collect(Collectors.toList());

        for (String parentId : directParentIds) {
            String forkId = findForkAncestor(definition, parentId, 0);
            if (forkId != null) return forkId;
        }
        return null;
    }

    private String findForkAncestor(ProcessDefinition definition, String nodeId, int depth) {
        if (depth > 20) return null; // guard against cycles

        Node n = definition.findNodeByIdSafe(nodeId).orElse(null);
        if (n == null) return null;
        if (Node.TYPE_FORK.equals(n.getType())) return n.getId();

        // Recurse upward
        List<String> parents = definition.getTransitions().stream()
                .filter(t -> t.getTargetId().equals(nodeId))
                .map(Transition::getSourceId)
                .collect(Collectors.toList());

        for (String parentId : parents) {
            String found = findForkAncestor(definition, parentId, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private List<String> outgoingTargets(ProcessDefinition definition, Node node) {
        return definition.getTransitionsFrom(node.getId()).stream()
                .map(Transition::getTargetId)
                .collect(Collectors.toList());
    }
}