package com.workflow.bpm.processtype;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/process-types")
@RequiredArgsConstructor
@Tag(name = "Process Types", description = "Manage types of tramites/processes")
public class ProcessTypeController {

    private final ProcessTypeService processTypeService;

    @GetMapping
    @Operation(summary = "List all active process types")
    public ResponseEntity<List<ProcessType>> listActive() {
        return ResponseEntity.ok(processTypeService.findAllActive());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a process type by ID")
    public ResponseEntity<ProcessType> getById(@PathVariable String id) {
        return ResponseEntity.ok(processTypeService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new process type")
    public ResponseEntity<ProcessType> create(@RequestBody ProcessType processType) {
        return ResponseEntity.status(201).body(processTypeService.create(processType));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a process type")
    public ResponseEntity<ProcessType> update(@PathVariable String id,
                                              @RequestBody ProcessType req) {
        return ResponseEntity.ok(processTypeService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-delete a process type (sets isActive=false)")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        processTypeService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
