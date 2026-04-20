package com.workflow.bpm.policy;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRepository extends MongoRepository<ProcessDefinition, String> {

    // Consultas básicas
    List<ProcessDefinition> findByStatus(String status);
    
    List<ProcessDefinition> findByCreatedBy(String userId);
    
    Optional<ProcessDefinition> findByIdAndCreatedBy(String id, String userId);
    
    Optional<ProcessDefinition> findByName(String name);
    
    boolean existsByName(String name);

    // Consultas para el motor de workflow
    List<ProcessDefinition> findByStatusOrderByCreatedAtDesc(String status);
    
    @Query("{ 'status': 'ACTIVE' }")
    List<ProcessDefinition> findAllActive();
    
    @Query("{ 'status': 'PUBLISHED' }")
    List<ProcessDefinition> findAllPublished();

    // Búsqueda por nombre (case insensitive)
    List<ProcessDefinition> findByNameContainingIgnoreCase(String name);
    
    // Contar por estado
    long countByStatus(String status);
    
    // Buscar por creador y estado
    List<ProcessDefinition> findByCreatedByAndStatus(String userId, String status);
}