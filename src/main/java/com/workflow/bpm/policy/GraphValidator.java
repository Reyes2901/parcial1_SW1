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

        // Validar START y END
        long startCount = def.getNodes().stream()
                .filter(n -> "START".equals(n.getType())).count();
        long endCount = def.getNodes().stream()
                .filter(n -> "END".equals(n.getType())).count();

        if (startCount != 1) {
            errors.add("Debe haber exactamente 1 nodo START (encontrados: " + startCount + ")");
        }
        if (endCount < 1) {
            errors.add("Debe haber al menos 1 nodo END");
        }

        // Verificar que toda transición apunta a nodos existentes
        Set<String> nodeIds = def.getNodes().stream()
                .map(Node::getId).collect(Collectors.toSet());

        def.getTransitions().forEach(t -> {
            if (!nodeIds.contains(t.getSourceId())) {
                errors.add("Transición " + t.getId() + ": sourceId '" + t.getSourceId() + "' no existe");
            }
            if (!nodeIds.contains(t.getTargetId())) {
                errors.add("Transición " + t.getId() + ": targetId '" + t.getTargetId() + "' no existe");
            }
        });

        // Verificar que todo nodo ACTIVITY tenga formSchema
        def.getNodes().stream()
                .filter(n -> "ACTIVITY".equals(n.getType()) && n.getFormSchema() == null)
                .forEach(n -> errors.add("Nodo ACTIVITY '" + n.getLabel() + "' no tiene formSchema"));

        if (!errors.isEmpty()) {
            throw new InvalidProcessException("Diagrama inválido: " + String.join(", ", errors));
        }
    }
}