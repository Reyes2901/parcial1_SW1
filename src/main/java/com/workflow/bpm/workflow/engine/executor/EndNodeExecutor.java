package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.notification.NotificationService;
import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component("END")
@RequiredArgsConstructor
@Slf4j
public class EndNodeExecutor implements NodeExecutor {

    private final ProcessInstanceRepository instanceRepo;
    private final NotificationService notificationService;
    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition, 
                                Node node) {
        
        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeLabel(node.getLabel());
        instance.setStatus(ProcessInstance.STATUS_COMPLETED);
        instance.setCompletedAt(Instant.now());
        
        
        instanceRepo.save(instance);
        notificationService.notifyInstanceAdvanced(instance);
        log.info("[END] Proceso '{}' completado en {}", 
                 instance.getId(), instance.getCompletedAt());
        
        return Collections.emptyList();
    }
}