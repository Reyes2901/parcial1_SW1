package com.workflow.bpm.department;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.user.User;
import com.workflow.bpm.user.UserRepository;
import com.workflow.bpm.department.dto.DepartmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public List<Department> findAllActive() {
        return departmentRepository.findByIsActiveTrue();
    }

    public Department findById(String id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
    }

    public Department create(DepartmentRequest req) {
        Department dept = Department.builder()
                .name(req.getName())
                .description(req.getDescription())
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        return departmentRepository.save(dept);
    }

    public Department update(String id, DepartmentRequest req) {
        Department dept = findById(id);
        dept.setName(req.getName());
        dept.setDescription(req.getDescription());
        return departmentRepository.save(dept);
    }

    public void softDelete(String id) {
        Department dept = findById(id);
        dept.setActive(false);
        departmentRepository.save(dept);
    }

    public List<User> getUsersByDepartment(String departmentId) {
        return userRepository.findByDepartmentId(departmentId);
    }
}
