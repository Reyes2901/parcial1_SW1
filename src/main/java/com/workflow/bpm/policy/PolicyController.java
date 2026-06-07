package com.workflow.bpm.policy;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate; // 👈 IMPORTANTE PARA WEBSOCKETS
import com.workflow.bpm.policy.dto.AiGenerateRequest;
import com.workflow.bpm.policy.dto.PolicySummaryDTO;
import com.workflow.bpm.shared.model.FormSchema;
import com.workflow.bpm.shared.model.Node;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@Tag(name = "Policies", description = "Policy/process definition management")
public class PolicyController {

    private final PolicyService service;
    private final AiService aiService;
    private final SimpMessagingTemplate messagingTemplate; // 👈 INYECTADO PARA TIEMPO REAL

    private PolicySummaryDTO toSummary(ProcessDefinition p) {
        return new PolicySummaryDTO(
                p.getId(),
                p.getName(),
                p.getStatus(),
                p.getVersion(),
                p.getDescription(),
                p.getCreatedBy(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getNodes() != null ? p.getNodes().size() : 0,
                p.getLanes() != null ? p.getLanes().size() : 0,
                p.getTransitions() != null ? p.getTransitions().size() : 0);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new policy definition")
    public ResponseEntity<ProcessDefinition> create(
            @Valid @RequestBody ProcessDefinition def,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(201).body(service.create(def, user.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a policy definition layout")
    public ResponseEntity<ProcessDefinition> update(
            @PathVariable String id,
            @Valid @RequestBody ProcessDefinition def,
            @AuthenticationPrincipal UserDetails user) {

        ProcessDefinition existing = service.findById(id);

        if (ProcessDefinition.STATUS_DRAFT.equals(existing.getStatus())
                && !existing.getCreatedBy().equals(user.getUsername())) {
            return ResponseEntity.status(403).build();
        }

        def.setStatus(existing.getStatus());
        def.setCreatedBy(existing.getCreatedBy());
        // Blindaje defensivo: el XML colaborativo no se actualiza por PUT REST.
        def.setBpmnXml(null);

        ProcessDefinition updated = service.update(id, def, user.getUsername());

        // 🚀 TIEMPO REAL: Cada vez que se haga un PUT, notificamos a los demás por WS
        // automáticamente
        Map<String, Object> payload = new HashMap<>();
        payload.put("bpmnXml", updated.getBpmnXml()); // Revisa si tu propiedad se llama getXml() o getBpmnXml()
        payload.put("updatedBy", user.getUsername());
        messagingTemplate.convertAndSend("/topic/policy/" + id, payload);

        return ResponseEntity.ok(updated);
    }

    // El único método para buscar por ID (Se eliminó el duplicado de abajo)
    @GetMapping("/{id}")
    @Operation(summary = "Get a policy by ID")
    public ResponseEntity<ProcessDefinition> getById(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {

        ProcessDefinition def = service.findById(id);

        if (ProcessDefinition.STATUS_DRAFT.equals(def.getStatus())) {
            if (user == null || !def.getCreatedBy().equals(user.getUsername())) {
                return ResponseEntity.status(403).build();
            }
        }

        return ResponseEntity.ok(def);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish a policy (validate graph and activate)")
    public ResponseEntity<PolicySummaryDTO> publish(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(toSummary(service.publish(id, user.getUsername())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate a policy")
    public ResponseEntity<PolicySummaryDTO> activate(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(toSummary(service.activate(id, user.getUsername())));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive a policy")
    public ResponseEntity<ProcessDefinition> archive(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.archive(id, user.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a policy definition")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        service.delete(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List all published policies")
    public ResponseEntity<List<PolicySummaryDTO>> listPublished() {
        List<PolicySummaryDTO> dtos = service.findPublished().stream()
                .map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @Operation(summary = "List all active policies")
    public ResponseEntity<List<PolicySummaryDTO>> listActive() {
        List<PolicySummaryDTO> dtos = service.findActive().stream()
                .map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my-drafts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List current admin's draft policies")
    public ResponseEntity<List<PolicySummaryDTO>> myDrafts(
            @AuthenticationPrincipal UserDetails user) {
        List<PolicySummaryDTO> dtos = service.findByCreator(user.getUsername()).stream()
                .map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my-policies")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List current admin's policies")
    public ResponseEntity<List<PolicySummaryDTO>> myPolicies(
            @AuthenticationPrincipal UserDetails user) {
        List<PolicySummaryDTO> dtos = service.findByCreator(user.getUsername()).stream()
                .map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/ai/generate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generate a process definition diagram using AI")
    public ResponseEntity<ProcessDefinition> generateWithAI(
            @Valid @RequestBody AiGenerateRequest req,
            @AuthenticationPrincipal UserDetails user) {

        // TEMPORAL (diagnóstico): try-catch explícito para forzar el stack trace
        // real en consola antes de que cualquier handler global lo procese.
        try {
            ProcessDefinition def = aiService.generateDiagram(
                    req.getPrompt(), req.getLanguage(), req.getExistingLanes());

            def.setCreatedBy(user.getUsername());
            def.setStatus(ProcessDefinition.STATUS_DRAFT);
            def.setVersion("1.0");

            ProcessDefinition saved = service.save(def);
            return ResponseEntity.status(201).body(saved);
        } catch (Exception e) {
            log.error("ERROR CRÍTICO EN BACKEND (generateWithAI): ", e);
            throw e;
        }
    }

    @GetMapping("/{policyId}/start-form")
    @Operation(summary = "Get the initial form schema required to start a process")
    public ResponseEntity<FormSchema> getStartForm(@PathVariable String policyId) {
        ProcessDefinition def = service.findById(policyId);

        FormSchema schema = def.getNodes().stream()
                .filter(Node::isStart)
                .findFirst()
                .map(Node::getFormSchema)
                .orElseGet(() -> def.getNodes().stream()
                        .filter(Node::isActivity)
                        .findFirst()
                        .map(Node::getFormSchema)
                        .orElse(null));

        if (schema == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(schema);
    }
}