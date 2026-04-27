package com.workflow.bpm.department;

import com.workflow.bpm.department.dto.DepartmentRequest;
import com.workflow.bpm.user.User;
import com.workflow.bpm.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department management endpoints")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "List all active departments")
    public ResponseEntity<List<Department>> listActive() {
        return ResponseEntity.ok(departmentService.findAllActive());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new department")
    public ResponseEntity<Department> create(@RequestBody DepartmentRequest req) {
        return ResponseEntity.status(201).body(departmentService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing department")
    public ResponseEntity<Department> update(@PathVariable String id,
                                             @RequestBody DepartmentRequest req) {
        return ResponseEntity.ok(departmentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-delete a department (sets isActive=false)")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        departmentService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users assigned to a department")
    public ResponseEntity<List<UserResponse>> getUsersByDepartment(@PathVariable String id) {
        List<User> users = departmentService.getUsersByDepartment(id);
        List<UserResponse> response = users.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
