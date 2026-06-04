package com.workflow.bpm.policy;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.workflow.bpm.policy.dto.AiGenerateRequest;
import com.workflow.bpm.policy.dto.PolicySummaryDTO;
import com.workflow.bpm.shared.model.FormSchema;
import com.workflow.bpm.shared.model.Node;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@Tag(name = "Policies", description = "Policy/process definition management")
public class PolicyController {

    private final PolicyService service;
    private final AiService aiService;

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
                p.getNodes()       != null ? p.getNodes().size()       : 0,
                p.getLanes()       != null ? p.getLanes().size()       : 0,
                p.getTransitions() != null ? p.getTransitions().size() : 0
        );
    }
     // Generar diagrama con IA
    // Solo ADMIN puede crear políticas
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new policy definition")
    public ResponseEntity<ProcessDefinition> create(
            @Valid @RequestBody ProcessDefinition def,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(201).body(service.create(def, user.getUsername()));
    }

    // Solo ADMIN puede actualizar políticas
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a policy definition")
    public ResponseEntity<ProcessDefinition> update(
            @PathVariable String id,
            @Valid @RequestBody ProcessDefinition def,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.update(id, def, user.getUsername()));
    }

    // Publicar: valida el grafo y cambia status a PUBLISHED/ACTIVE
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish a policy (validate graph and activate)")
    public ResponseEntity<PolicySummaryDTO> publish(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(toSummary(service.publish(id, user.getUsername())));
    }

    // Activar (alias de publish)
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate a policy")
    public ResponseEntity<PolicySummaryDTO> activate(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(toSummary(service.activate(id, user.getUsername())));
    }

    // Archivar
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive a policy")
    public ResponseEntity<ProcessDefinition> archive(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.archive(id, user.getUsername()));
    }

    // Eliminar
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a policy definition")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        service.delete(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    // Cualquier usuario autenticado puede listar políticas publicadas
    @GetMapping
    @Operation(summary = "List all published policies")
    public ResponseEntity<List<PolicySummaryDTO>> listPublished() {
        List<PolicySummaryDTO> dtos = service.findPublished().stream()
                .map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // Listar activas
    @GetMapping("/active")
    @Operation(summary = "List all active policies")
    public ResponseEntity<List<PolicySummaryDTO>> listActive() {
        List<PolicySummaryDTO> dtos = service.findActive().stream()
                .map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // Ver una política por ID
    @GetMapping("/{id}")
    @Operation(summary = "Get a policy by ID")
    public ResponseEntity<ProcessDefinition> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    // El admin ve sus borradores
    @GetMapping("/my-drafts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List current admin's draft policies")
    public ResponseEntity<List<PolicySummaryDTO>> myDrafts(
            @AuthenticationPrincipal UserDetails user) {
        List<PolicySummaryDTO> dtos = service.findByCreator(user.getUsername()).stream()
                .map(this::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // El admin ve todas sus políticas
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

        ProcessDefinition def = aiService.generateDiagram(
                req.getPrompt(), req.getLanguage(), req.getExistingLanes());

        // Guardar como borrador — el admin puede editarlo antes de publicar
        def.setCreatedBy(user.getUsername());
        def.setStatus(ProcessDefinition.STATUS_DRAFT);
        def.setVersion("1.0");

        ProcessDefinition saved = service.save(def);
        return ResponseEntity.status(201).body(saved);
    }

    /**
     * Returns the form schema the client must fill to start a process.
     * Looks for the START node's formSchema; falls back to the first ACTIVITY node.
     */
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