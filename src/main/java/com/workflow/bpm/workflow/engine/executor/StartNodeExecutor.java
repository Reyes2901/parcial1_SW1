package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.shared.model.Transition;
import com.workflow.bpm.workflow.document.ProcessInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component("START")
@RequiredArgsConstructor
@Slf4j
public class StartNodeExecutor implements NodeExecutor {
    
    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition, 
                                Node node) {
        log.info("[START] Proceso iniciado: {}", instance.getId());
             
        List<Transition> salidas = definition.getTransitionsFrom(node.getId());
        
        if (salidas.isEmpty()) {
            log.warn("[START] No hay transiciones de salida desde el nodo START");
            return List.of();
        }
        
        return salidas.stream()
                .map(Transition::getTargetId)
                .collect(Collectors.toList());
    }
}