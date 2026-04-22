package com.workflow.bpm.task.document;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TaskInstanceRepository extends MongoRepository<TaskInstance, String> {
    
    List<TaskInstance> findByAssigneeIdAndStatusIn(String assigneeId, List<String> statuses);
    
    List<TaskInstance> findByAssigneeRoleAndStatusIn(String assigneeRole, List<String> statuses);  // 👈 Nuevo
    
    List<TaskInstance> findByAssigneeId(String assigneeId);
    
    List<TaskInstance> findByInstanceId(String instanceId);
    
    List<TaskInstance> findByStatusAndDueAtBefore(String status, Instant now);
    
    List<TaskInstance> findByStatusInAndDueAtBefore(List<String> statuses, Instant now);

    long countByInstanceIdAndStatus(String instanceId, String status);
    
    List<TaskInstance> findByStatus(String status);

    long countByStatus(String statusPending);
}