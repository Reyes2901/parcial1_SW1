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
import java.util.stream.Collectors;

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
        
        // ❌ ELIMINADO: instance.addAuditEntry(...)
        
        List<Transition> candidates = definition.getTransitionsFrom(node.getId());

        List<Transition> all = evaluator.resolveTransitions(
                candidates, instance.getVariables(), true);

        List<String> nextNodeIds = all.stream()
                .map(Transition::getTargetId)
                .collect(Collectors.toList());

        log.info("[FORK] '{}' → {} ramas paralelas: {}",
                node.getLabel(), nextNodeIds.size(), nextNodeIds);
        
        return nextNodeIds;
    }
}