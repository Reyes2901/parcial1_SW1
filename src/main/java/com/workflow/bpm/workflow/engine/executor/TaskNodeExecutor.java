package com.workflow.bpm.workflow.engine.executor;

import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.model.Node;
import com.workflow.bpm.workflow.document.ProcessInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TASK is a UI-friendly alias for ACTIVITY.
 * Delegates entirely to ActivityNodeExecutor so there is no duplicated logic.
 */
@Component("TASK")
@RequiredArgsConstructor
public class TaskNodeExecutor implements NodeExecutor {

    private final ActivityNodeExecutor activityNodeExecutor;

    @Override
    public List<String> execute(ProcessInstance instance,
                                ProcessDefinition definition,
                                Node node) {
        return activityNodeExecutor.execute(instance, definition, node);
    }
}
