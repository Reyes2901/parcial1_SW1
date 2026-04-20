package com.workflow.bpm.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {
    
    private final PolicyService policyService;
    
    @PostMapping
    public ResponseEntity<ProcessDefinition> create(@RequestBody ProcessDefinition policy) {
        String username = getCurrentUsername();
        ProcessDefinition created = policyService.create(policy, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping
    public List<ProcessDefinition> getAll() {
        return policyService.findAll();
    }
    
    @GetMapping("/active")
    public List<ProcessDefinition> getActive() {
        return policyService.findActive();
    }
    
    @GetMapping("/{id}")
    public ProcessDefinition getById(@PathVariable String id) {
        return policyService.findById(id);
    }
    
    @PutMapping("/{id}")
    public ProcessDefinition update(@PathVariable String id, @RequestBody ProcessDefinition policy) {
        return policyService.update(id, policy);
    }
    
    @PostMapping("/{id}/activate")
    public ProcessDefinition activate(@PathVariable String id) {
        return policyService.activate(id);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        policyService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}