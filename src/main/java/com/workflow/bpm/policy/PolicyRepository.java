package com.workflow.bpm.policy;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRepository extends MongoRepository<ProcessDefinition, String> {

    Optional<ProcessDefinition> findByName(String name);

    List<ProcessDefinition> findByStatus(String status);

    List<ProcessDefinition> findByCreatedBy(String createdBy);

    boolean existsByName(String name);

    List<ProcessDefinition> findByStatusAndNameContainingIgnoreCase(String status, String name);

    // Consulta personalizada con @Query
    @Query("{ 'status': ?0, 'createdAt': { $gte: ?1 } }")
    List<ProcessDefinition> findActiveCreatedAfter(String status, java.time.Instant date);

    // Contar procesos por estado
    long countByStatus(String status);

    // Buscar procesos que contengan un texto en nombre o descripción
    @Query("{ $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }")
    List<ProcessDefinition> searchByNameOrDescription(String searchTerm);
}