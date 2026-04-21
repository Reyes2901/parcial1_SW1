package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.workflow.document.ProcessInstance;

import java.util.List;

public interface NodeExecutor {
    /**
     * Ejecuta la lógica del nodo y devuelve los IDs de los
     * nodos siguientes a activar (puede ser 1 o varios en paralelo).
     */
    List<String> execute(ProcessInstance instance,
                         ProcessDefinition definition,
                         Node node);
}