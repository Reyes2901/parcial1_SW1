package com.workflow.bpm.policy;

import com.workflow.bpm.shared.exception.InvalidProcessException;
import com.workflow.bpm.shared.model.Node;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GraphValidator {

    public void validate(ProcessDefinition def) {
        List<String> errors = new ArrayList<>();

        // ── 1. Exactly one START, at least one END ───────────────────
        long startCount = def.getNodes().stream()
                .filter(n -> Node.TYPE_START.equals(n.getType())).count();
        long endCount   = def.getNodes().stream()
                .filter(n -> Node.TYPE_END.equals(n.getType())).count();

        if (startCount != 1) {
            errors.add("Debe haber exactamente 1 nodo START (encontrados: " + startCount + ")");
        }
        if (endCount < 1) {
            errors.add("Debe haber al menos 1 nodo END");
        }

        // ── 2. All transition endpoints must exist ───────────────────
        Set<String> nodeIds = def.getNodes().stream()
                .map(Node::getId).collect(Collectors.toSet());

        def.getTransitions().forEach(t -> {
            if (!nodeIds.contains(t.getSourceId())) {
                errors.add("Transición " + t.getId()
                        + ": sourceId '" + t.getSourceId() + "' no existe");
            }
            if (!nodeIds.contains(t.getTargetId())) {
                errors.add("Transición " + t.getId()
                        + ": targetId '" + t.getTargetId() + "' no existe");
            }
        });

        // ── 3. ACTIVITY/TASK nodes should have a formSchema ──────────
        def.getNodes().stream()
                .filter(n -> (Node.TYPE_ACTIVITY.equals(n.getType()) || Node.TYPE_TASK.equals(n.getType()))
                          && n.getFormSchema() == null)
                .forEach(n -> errors.add(
                        "Nodo ACTIVITY/TASK '" + n.getLabel() + "' no tiene formSchema"));

        // ── 4. FORK must have ≥ 2 outgoing transitions ───────────────
        def.getNodes().stream()
                .filter(n -> Node.TYPE_FORK.equals(n.getType()))
                .forEach(forkNode -> {
                    long outgoing = def.getTransitions().stream()
                            .filter(t -> t.getSourceId().equals(forkNode.getId()))
                            .count();
                    if (outgoing < 2) {
                        errors.add("Nodo FORK '" + forkNode.getLabel()
                                + "' debe tener al menos 2 transiciones de salida (tiene: "
                                + outgoing + ")");
                    }
                });

        // ── 5. JOIN must have ≥ 2 incoming transitions ───────────────
        def.getNodes().stream()
                .filter(n -> Node.TYPE_JOIN.equals(n.getType()))
                .forEach(joinNode -> {
                    long incoming = def.getTransitions().stream()
                            .filter(t -> t.getTargetId().equals(joinNode.getId()))
                            .count();
                    if (incoming < 2) {
                        errors.add("Nodo JOIN '" + joinNode.getLabel()
                                + "' debe tener al menos 2 transiciones de entrada (tiene: "
                                + incoming + ")");
                    }
                });

        // ── 6. DECISION must have ≥ 2 outgoing transitions ──────────
        def.getNodes().stream()
                .filter(n -> Node.TYPE_DECISION.equals(n.getType()))
                .forEach(decisionNode -> {
                    long outgoing = def.getTransitions().stream()
                            .filter(t -> t.getSourceId().equals(decisionNode.getId()))
                            .count();
                    if (outgoing < 2) {
                        errors.add("Nodo DECISION '" + decisionNode.getLabel()
                                + "' debe tener al menos 2 transiciones de salida (tiene: "
                                + outgoing + ")");
                    }
                });

        // ── 7. No orphan nodes (no incoming AND not START) ───────────
        Set<String> hasIncoming = def.getTransitions().stream()
                .map(t -> t.getTargetId())
                .collect(Collectors.toSet());

        def.getNodes().stream()
                .filter(n -> !Node.TYPE_START.equals(n.getType())
                          && !hasIncoming.contains(n.getId()))
                .forEach(n -> errors.add(
                        "Nodo '" + n.getLabel() + "' [" + n.getType() + "] no tiene ninguna transición de entrada (nodo huérfano)"));

        if (!errors.isEmpty()) {
            throw new InvalidProcessException("Diagrama inválido: " + String.join("; ", errors));
        }
    }
}