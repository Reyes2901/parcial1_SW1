package com.workflow.bpm.workflow.document;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessInstanceRepository extends MongoRepository<ProcessInstance, String> {
    
    List<ProcessInstance> findByClientId(String clientId);
    List<ProcessInstance> findByDefinitionIdAndStatus(String definitionId, String status);
    List<ProcessInstance> findByStatus(String status);
    // List<ProcessInstance> findByCurrentNodeId(String nodeId);
    Optional<ProcessInstance> findByClientIdAndDefinitionId(String clientId, String definitionId);
}