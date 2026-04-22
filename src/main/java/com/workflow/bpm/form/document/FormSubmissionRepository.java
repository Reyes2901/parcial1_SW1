package com.workflow.bpm.form.document;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormSubmissionRepository extends MongoRepository<FormSubmission, String> {
    
    List<FormSubmission> findByInstanceId(String instanceId);
    
    Optional<FormSubmission> findByTaskId(String taskId);
}