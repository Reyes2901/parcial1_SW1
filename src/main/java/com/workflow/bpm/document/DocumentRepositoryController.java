package com.workflow.bpm.document;

import com.workflow.bpm.document.dto.AttachRequest;
import com.workflow.bpm.document.dto.DocumentEntryDTO;
import com.workflow.bpm.document.dto.DocumentPermissionsDTO;
import com.workflow.bpm.document.dto.GrantRoleRequest;
import com.workflow.bpm.document.dto.GrantUserRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Repositorio Documental Empresarial (Fase 1).
 * <p>
 * Capa de organización sobre el almacenamiento de archivos actual. NO sube ni
 * elimina binarios; gestiona referencias lógicas, permisos y descarga con ACL.
 * Comparte el prefijo {@code /api/files} sin modificar las rutas existentes.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "Document Repository", description = "Repositorio documental por trámite y por política")
public class DocumentRepositoryController {

    private final DocumentRepositoryService service;

    @GetMapping("/repository/instance/{instanceId}")
    @Operation(summary = "List documents associated with a process instance")
    public ResponseEntity<List<DocumentEntryDTO>> listByInstance(
            @PathVariable String instanceId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.listByInstance(instanceId, user));
    }

    @GetMapping("/repository/policy/{policyId}")
    @Operation(summary = "List documents associated with a policy")
    public ResponseEntity<List<DocumentEntryDTO>> listByPolicy(
            @PathVariable String policyId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.listByPolicy(policyId, user));
    }

    @PostMapping("/repository/attach")
    @Operation(summary = "Attach an existing file to the document repository")
    public ResponseEntity<DocumentEntryDTO> attach(
            @Valid @RequestBody AttachRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(201).body(service.attach(req, user.getUsername()));
    }

    @GetMapping("/repository/{entryId}/download")
    @Operation(summary = "Download a repository document (access controlled)")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable String entryId,
            @AuthenticationPrincipal UserDetails user) {
        DocumentRepositoryService.DownloadResource dr = service.resolveForDownload(entryId, user);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dr.filename() + "\"")
                .contentType(MediaType.parseMediaType(dr.contentType()));
        if (dr.contentLength() >= 0) {
            builder.contentLength(dr.contentLength());
        }
        return builder.body(dr.resource());
    }

    @DeleteMapping("/repository/{entryId}")
    @Operation(summary = "Soft-delete a repository document reference")
    public ResponseEntity<Void> softDelete(
            @PathVariable String entryId,
            @AuthenticationPrincipal UserDetails user) {
        service.softDelete(entryId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/grant-user")
    @Operation(summary = "Grant document read access to a user")
    public ResponseEntity<DocumentPermissionsDTO> grantUser(
            @PathVariable String id,
            @Valid @RequestBody GrantUserRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.grantUser(id, req.getUserId(), user));
    }

    @PostMapping("/{id}/grant-role")
    @Operation(summary = "Grant document read access to a role")
    public ResponseEntity<DocumentPermissionsDTO> grantRole(
            @PathVariable String id,
            @Valid @RequestBody GrantRoleRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.grantRole(id, req.getRole(), user));
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "Get authorized users and roles of a document")
    public ResponseEntity<DocumentPermissionsDTO> getPermissions(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.getPermissions(id, user));
    }
}
