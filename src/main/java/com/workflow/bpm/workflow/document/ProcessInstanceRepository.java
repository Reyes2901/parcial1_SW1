package com.workflow.bpm.workflow.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ProcessInstanceRepository extends MongoRepository<ProcessInstance, String> {
    
    List<ProcessInstance> findByClientId(String clientId);
    List<ProcessInstance> findByClientIdAndStatus(String clientId, String status);
    
    List<ProcessInstance> findByDefinitionIdAndStatus(String definitionId, String status);
    
    List<ProcessInstance> findByStatus(String status);
    
    // Paginated admin queries
    Page<ProcessInstance> findAll(Pageable pageable);
    Page<ProcessInstance> findByStatus(String status, Pageable pageable);
    Page<ProcessInstance> findByDefinitionId(String definitionId, Pageable pageable);
    Page<ProcessInstance> findByStatusAndDefinitionId(String status, String definitionId, Pageable pageable);
    
    // Métodos de conteo para Analytics
    long countByDefinitionId(String definitionId);
    long countByDefinitionIdAndStatus(String definitionId, String status);
    long countByStatus(String status);
    long countByStatusAndCompletedAtAfter(String status, Instant after);
}