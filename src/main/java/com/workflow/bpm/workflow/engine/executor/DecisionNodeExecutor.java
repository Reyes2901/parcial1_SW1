package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.shared.model.Transition;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.engine.TransitionEvaluator;
import com.workflow.bpm.workflow.engine.WorkflowException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("DECISION")
@RequiredArgsConstructor
@Slf4j
public class DecisionNodeExecutor implements NodeExecutor {

    private final TransitionEvaluator evaluator;

    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition, 
                                Node node) {
        
        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeLabel(node.getLabel());
        
        // ❌ ELIMINADO: instance.addAuditEntry(...)
        
        List<Transition> candidates = definition.getTransitionsFrom(node.getId());

        if (candidates.isEmpty()) {
            throw new WorkflowException("DECISION '" + node.getLabel() 
                    + "': no tiene transiciones de salida");
        }

        List<Transition> chosen = evaluator.resolveTransitions(
                candidates, instance.getVariables(), false);

        if (chosen.isEmpty()) {
            Transition defaultTransition = evaluator.findDefaultTransition(candidates);
            if (defaultTransition != null) {
                log.info("[DECISION] '{}' → transición por defecto '{}'",
                        node.getLabel(), defaultTransition.getLabel());
                return List.of(defaultTransition.getTargetId());
            }
            
            throw new WorkflowException("DECISION '" + node.getLabel()
                    + "': ninguna condición es verdadera. Variables: "
                    + instance.getVariables());
        }

        Transition t = chosen.get(0);
        log.info("[DECISION] '{}' → transición '{}' ({})",
                node.getLabel(), t.getId(), t.getLabel());
        
        return List.of(t.getTargetId());
    }
}