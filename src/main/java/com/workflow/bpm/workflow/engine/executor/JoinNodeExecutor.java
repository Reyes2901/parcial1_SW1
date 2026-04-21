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

@Component("JOIN")
@RequiredArgsConstructor
@Slf4j
public class JoinNodeExecutor implements NodeExecutor {

    private final TaskInstanceRepository taskRepo;

    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition, 
                                Node node) {
        
        
        long totalIncoming = definition.getTransitions().stream()
                .filter(t -> t.getTargetId().equals(node.getId()))
                .count();

        long completadas = taskRepo.findByInstanceId(instance.getId()).stream()
                .filter(t -> TaskInstance.STATUS_COMPLETED.equals(t.getStatus()))
                .count();

        log.info("[JOIN] '{}': {}/{} ramas completadas.", 
                 node.getLabel(), completadas, totalIncoming);
        
        if (completadas < totalIncoming) {
            instance.setCurrentNodeId(node.getId());
            instance.setCurrentNodeLabel(node.getLabel());
            return Collections.emptyList();
        }

        log.info("[JOIN] '{}': todas las ramas llegaron. Continuando.", node.getLabel());
        
        List<Transition> salidas = definition.getTransitionsFrom(node.getId());
        return salidas.stream()
                .map(Transition::getTargetId)
                .collect(Collectors.toList());
    }
}