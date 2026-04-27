package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.shared.model.Transition;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.engine.TransitionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FORK executor — parallel split.
 *
 * Generates a shared parallelGroupId and stores it in the ProcessInstance
 * variables under the key "_parallelGroupId_<forkNodeId>".
 * ActivityNodeExecutor reads this key when the next node belongs to a FORK branch
 * and stamps the same group ID on every TaskInstance it creates.
 */
@Component("FORK")
@RequiredArgsConstructor
@Slf4j
public class ForkNodeExecutor implements NodeExecutor {

    private final TransitionEvaluator evaluator;

    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition,
                                Node node) {

        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeLabel(node.getLabel());

        // Generate a single group ID shared by all parallel branches from this fork
        String groupId = UUID.randomUUID().toString();

        // Store it in instance variables so ActivityNodeExecutor can stamp tasks
        instance.getVariables().put("_parallelGroupId_" + node.getId(), groupId);
        // Also store the "active" fork node so downstream executors know the context
        instance.getVariables().put("_activeForkNodeId", node.getId());

        List<Transition> candidates = definition.getTransitionsFrom(node.getId());
        List<Transition> all = evaluator.resolveTransitions(candidates, instance.getVariables(), true);

        List<String> nextNodeIds = all.stream()
                .map(Transition::getTargetId)
                .collect(Collectors.toList());

        log.info("[FORK] '{}' → {} ramas paralelas: {} (groupId={})",
                node.getLabel(), nextNodeIds.size(), nextNodeIds, groupId);

        return nextNodeIds;
    }
}