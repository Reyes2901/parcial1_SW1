package com.workflow.bpm.user;

import com.workflow.bpm.user.dto.UserCreateRequest;
import com.workflow.bpm.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    @Operation(summary = "Register a new user")
    public ResponseEntity<?> create(@Valid @RequestBody UserCreateRequest req) {
        if (repo.existsByUsername(req.getUsername())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Username already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        User user = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole() != null && !req.getRole().isEmpty() ? req.getRole() : "USER")
                .departmentId(req.getDepartmentId())
                .build();

        User savedUser = repo.save(user);
        return ResponseEntity.ok(UserResponse.from(savedUser));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users with optional role/departmentId filter")
    public ResponseEntity<List<UserResponse>> getAll(
            @Parameter(description = "Filter by role (e.g. ADMIN, USER)")
            @RequestParam(required = false) String role,
            @Parameter(description = "Filter by department ID")
            @RequestParam(required = false) String departmentId) {

        List<User> users;

        if (role != null && departmentId != null) {
            users = repo.findByRoleAndDepartmentId(role, departmentId);
        } else if (role != null) {
            users = repo.findByRole(role);
        } else if (departmentId != null) {
            users = repo.findByDepartmentId(departmentId);
        } else {
            users = repo.findAll();
        }

        List<UserResponse> response = users.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserResponse> getById(@PathVariable String id) {
        return repo.findById(id)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }
}