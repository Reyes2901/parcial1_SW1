package com.workflow.bpm.processtype;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessTypeRepository extends MongoRepository<ProcessType, String> {
    List<ProcessType> findByIsActiveTrue();
    boolean existsByName(String name);
}
