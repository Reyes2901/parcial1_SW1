package com.workflow.bpm.task.document;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Repository
public interface TaskInstanceRepository extends MongoRepository<TaskInstance, String> {

    List<TaskInstance> findByAssigneeIdAndStatusIn(String assigneeId, List<String> statuses);

    List<TaskInstance> findByAssigneeRoleAndStatusIn(String assigneeRole, List<String> statuses);

    List<TaskInstance> findByAssigneeId(String assigneeId);

    List<TaskInstance> findByInstanceId(String instanceId);

    List<TaskInstance> findByInstanceIdIn(List<String> instanceIds);

    List<TaskInstance> findByStatusAndDueAtBefore(String status, Instant now);

    List<TaskInstance> findByStatusInAndDueAtBefore(List<String> statuses, Instant now);

    List<TaskInstance> findByStatusIn(List<String> statuses);

    long countByInstanceIdAndStatus(String instanceId, String status);

    List<TaskInstance> findByStatus(String status);

    long countByStatus(String status);

    // Parallel group support — used by JoinNodeExecutor
    List<TaskInstance> findByInstanceIdAndParallelGroupId(String instanceId, String parallelGroupId);

    long countByInstanceIdAndParallelGroupIdAndStatus(String instanceId, String parallelGroupId, String status);

    long countByInstanceIdAndParallelGroupId(String instanceId, String parallelGroupId);

    // Analytics
    long countByStatusInAndDueAtBefore(List<String> statuses, Date now);
    long countByAssigneeIdAndStatus(String assigneeId, String status);
    List<TaskInstance> findByLaneIdAndStatusIn(String laneId, List<String> statuses);
}