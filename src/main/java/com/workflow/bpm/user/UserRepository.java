package com.workflow.bpm.user;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByRole(String role);
    List<User> findByDepartmentId(String departmentId);
    List<User> findByRoleAndDepartmentId(String role, String departmentId);
    Optional<User> findFirstByRoleAndDepartmentId(String role, String departmentId);
}