package com.workflow.bpm.document;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepositoryRepository extends MongoRepository<DocumentRepositoryEntry, String> {

    List<DocumentRepositoryEntry> findByProcessInstanceIdAndDeletedFalse(String processInstanceId);

    List<DocumentRepositoryEntry> findByPolicyIdAndDeletedFalse(String policyId);

    List<DocumentRepositoryEntry> findByFileIdAndProcessInstanceIdAndDeletedFalse(String fileId, String processInstanceId);
}
