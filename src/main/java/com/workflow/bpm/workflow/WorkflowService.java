package com.workflow.bpm.workflow;

import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.user.User;
import com.workflow.bpm.user.UserRepository;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import com.workflow.bpm.workflow.engine.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final ProcessInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskRepo;
    private final UserRepository userRepo;
    private final WorkflowEngine engine;

    public ProcessInstance getInstance(String id) {
        return engine.getInstance(id);
    }

    public List<ProcessInstance> getByClient(String clientId) {
        return engine.getInstancesByClient(clientId);
    }

    public List<TaskInstance> getMyTasks(String username) {
        List<TaskInstance> allTasks = new ArrayList<>();
        
        // 1. Buscar tareas asignadas directamente al username
        List<TaskInstance> byAssignee = taskRepo.findByAssigneeIdAndStatusIn(
                username,
                List.of(TaskInstance.STATUS_PENDING, TaskInstance.STATUS_IN_PROGRESS)
        );
        allTasks.addAll(byAssignee);
        
        // 2. Buscar el rol del usuario y tareas asignadas a ese rol
        userRepo.findByUsername(username).ifPresent(user -> {
            if (user.getRole() != null) {
                List<TaskInstance> byRole = taskRepo.findByAssigneeRoleAndStatusIn(
                        user.getRole(),
                        List.of(TaskInstance.STATUS_PENDING, TaskInstance.STATUS_IN_PROGRESS)
                );
                allTasks.addAll(byRole);
            }
        });
        
        return allTasks;
    }
}