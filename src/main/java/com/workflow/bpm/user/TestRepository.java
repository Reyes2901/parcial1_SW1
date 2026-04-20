package com.workflow.bpm.user;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TestRepository extends MongoRepository<TestConnection, String> {
}
