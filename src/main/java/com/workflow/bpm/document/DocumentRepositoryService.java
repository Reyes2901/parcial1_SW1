package com.workflow.bpm.document;

import com.workflow.bpm.document.dto.AttachRequest;
import com.workflow.bpm.document.dto.DocumentEntryDTO;
import com.workflow.bpm.document.dto.DocumentPermissionsDTO;
import com.workflow.bpm.document.storage.FileStorageProvider;
import com.workflow.bpm.form.document.FormSubmission;
import com.workflow.bpm.form.document.FormSubmissionRepository;
import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.workflow.document.AuditEntry;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Capa de organización del Repositorio Documental Empresarial.
 * <p>
 * Crea referencias lógicas ({@link DocumentRepositoryEntry}) hacia archivos ya
 * existentes en disco, sin duplicar binarios ni tocar el upload/firma actuales.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentRepositoryService {

    private static final String FILE_URL_PREFIX = "/api/files/";
    private static final String SIGNATURE_MARKER = "/signatures/";

    // Tipos documentales detectados
    private static final String TYPE_PDF = "PDF";
    private static final String TYPE_WORD = "WORD";
    private static final String TYPE_EXCEL = "EXCEL";
    private static final String TYPE_IMAGE = "IMAGE";
    private static final String TYPE_SIGNATURE = "SIGNATURE";
    private static final String TYPE_UNKNOWN = "UNKNOWN";

    private final DocumentRepositoryRepository repo;
    private final ProcessInstanceRepository instanceRepo;
    private final FormSubmissionRepository formSubmissionRepo;
    private final DocumentAuditService auditService;
    private final DocumentAccessService accessService;
    private final FileStorageProvider storageProvider;

    // ─── Consultas ───────────────────────────────────────────────────────

    public List<DocumentEntryDTO> listByInstance(String instanceId, UserDetails user) {
        return repo.findByProcessInstanceIdAndDeletedFalse(instanceId).stream()
                .filter(e -> accessService.canRead(e, user))
                .map(DocumentEntryDTO::from)
                .collect(Collectors.toList());
    }

    public List<DocumentEntryDTO> listByPolicy(String policyId, UserDetails user) {
        return repo.findByPolicyIdAndDeletedFalse(policyId).stream()
                .filter(e -> accessService.canRead(e, user))
                .map(DocumentEntryDTO::from)
                .collect(Collectors.toList());
    }

    public DocumentPermissionsDTO getPermissions(String entryId, UserDetails user) {
        DocumentRepositoryEntry entry = findEntry(entryId);
        if (!accessService.canRead(entry, user)) {
            throw new AccessDeniedException("Sin permisos para ver este documento");
        }
        return DocumentPermissionsDTO.from(entry);
    }

    // ─── Asociación manual ───────────────────────────────────────────────

    public DocumentEntryDTO attach(AttachRequest req, String username) {
        ProcessInstance instance = instanceRepo.findById(req.getInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + req.getInstanceId()));

        boolean isSignature = req.getFileId() != null && req.getFileId().contains(SIGNATURE_MARKER);
        String documentType = (req.getDocumentType() != null && !req.getDocumentType().isBlank())
                ? req.getDocumentType()
                : detectDocumentType(req.getFileId(), isSignature);

        DocumentRepositoryEntry entry = DocumentRepositoryEntry.builder()
                .fileId(req.getFileId())
                .processInstanceId(instance.getId())
                .policyId(instance.getDefinitionId())
                .taskId(req.getTaskId())
                .uploadedBy(username)
                .uploadedAt(Instant.now())
                .documentType(documentType)
                .required(req.isRequired())
                .build();
        applyPhysicalMetadata(entry);

        DocumentRepositoryEntry saved = repo.save(entry);

        auditService.record(instance.getId(), AuditEntry.ACTION_DOCUMENT_UPLOADED, username,
                metadata("fileId", req.getFileId(), "documentType", documentType, "entryId", saved.getId()));

        log.info("📎 Documento asociado al repositorio — entry: '{}', instancia: '{}', usuario: '{}'",
                saved.getId(), instance.getId(), username);
        return DocumentEntryDTO.from(saved);
    }

    // ─── Permisos ────────────────────────────────────────────────────────

    public DocumentPermissionsDTO grantUser(String entryId, String userId, UserDetails actor) {
        DocumentRepositoryEntry entry = findEntry(entryId);
        requireManage(entry, actor);

        if (entry.getAllowedUsers() == null) {
            entry.setAllowedUsers(new ArrayList<>());
        }
        if (!entry.getAllowedUsers().contains(userId)) {
            entry.getAllowedUsers().add(userId);
            repo.save(entry);
            auditService.record(entry.getProcessInstanceId(),
                    AuditEntry.ACTION_DOCUMENT_PERMISSION_GRANTED, actor.getUsername(),
                    metadata("entryId", entryId, "targetType", "USER", "target", userId));
        }
        return DocumentPermissionsDTO.from(entry);
    }

    public DocumentPermissionsDTO grantRole(String entryId, String role, UserDetails actor) {
        DocumentRepositoryEntry entry = findEntry(entryId);
        requireManage(entry, actor);

        if (entry.getAllowedRoles() == null) {
            entry.setAllowedRoles(new ArrayList<>());
        }
        if (!entry.getAllowedRoles().contains(role)) {
            entry.getAllowedRoles().add(role);
            repo.save(entry);
            auditService.record(entry.getProcessInstanceId(),
                    AuditEntry.ACTION_DOCUMENT_PERMISSION_GRANTED, actor.getUsername(),
                    metadata("entryId", entryId, "targetType", "ROLE", "target", role));
        }
        return DocumentPermissionsDTO.from(entry);
    }

    // ─── Descarga con control de acceso ──────────────────────────────────

    public DownloadResource resolveForDownload(String entryId, UserDetails user) {
        DocumentRepositoryEntry entry = findEntry(entryId);
        if (!accessService.canRead(entry, user)) {
            throw new AccessDeniedException("Sin permisos para descargar este documento");
        }

        String fileId = entry.getFileId();
        if (!storageProvider.exists(fileId)) {
            throw new ResourceNotFoundException("Archivo físico no encontrado: " + fileId);
        }
        try {
            // Streaming: el InputStream se copia por chunks al response; nunca se
            // carga el archivo completo en memoria.
            InputStream in = storageProvider.read(fileId);
            Resource resource = new InputStreamResource(in);

            String contentType = storageProvider.contentType(fileId);
            long size = storageProvider.size(fileId);
            String filename = extractFilename(fileId);

            auditService.record(entry.getProcessInstanceId(),
                    AuditEntry.ACTION_DOCUMENT_DOWNLOADED, user.getUsername(),
                    metadata("entryId", entryId, "fileId", fileId));
            return new DownloadResource(resource, filename, contentType, size);
        } catch (IOException e) {
            throw new ResourceNotFoundException("No se pudo leer el archivo: " + fileId);
        }
    }

    // ─── Borrado lógico ──────────────────────────────────────────────────

    public void softDelete(String entryId, UserDetails actor) {
        DocumentRepositoryEntry entry = findEntry(entryId);
        requireManage(entry, actor);

        entry.setDeleted(true);
        repo.save(entry);
        auditService.record(entry.getProcessInstanceId(),
                AuditEntry.ACTION_DOCUMENT_DELETED, actor.getUsername(),
                metadata("entryId", entryId, "fileId", entry.getFileId()));
        log.info("🗑 Documento borrado lógicamente — entry: '{}', usuario: '{}'", entryId, actor.getUsername());
    }

    // ─── Asociación automática al completar tarea ────────────────────────

    /**
     * Crea automáticamente entradas del repositorio para todos los archivos
     * aportados al completar una tarea. Reúne URLs desde el formData y desde el
     * FormSubmission (attachmentUrls + signatureUrl) cuando exista.
     * <p>
     * Nunca lanza: los fallos se registran sin romper el flujo del workflow.
     */
    public void autoAttachFromCompletedTask(TaskInstance task, ProcessInstance instance,
                                            Map<String, Object> formData, String username) {
        try {
            Set<String> urls = collectFileUrls(task, formData);
            if (urls.isEmpty()) {
                return;
            }
            for (String url : urls) {
                if (!repo.findByFileIdAndProcessInstanceIdAndDeletedFalse(url, instance.getId()).isEmpty()) {
                    continue; // ya asociado
                }
                boolean isSignature = url.contains(SIGNATURE_MARKER);
                String documentType = detectDocumentType(url, isSignature);

                DocumentRepositoryEntry entry = DocumentRepositoryEntry.builder()
                        .fileId(url)
                        .processInstanceId(instance.getId())
                        .policyId(instance.getDefinitionId())
                        .taskId(task.getId())
                        .uploadedBy(username)
                        .uploadedAt(Instant.now())
                        .documentType(documentType)
                        .build();
                applyPhysicalMetadata(entry);
                DocumentRepositoryEntry saved = repo.save(entry);

                String action = isSignature
                        ? AuditEntry.ACTION_DOCUMENT_SIGNED
                        : AuditEntry.ACTION_DOCUMENT_UPLOADED;
                auditService.record(instance.getId(), action, username,
                        metadata("entryId", saved.getId(), "fileId", url, "taskId", task.getId()));
            }
            log.info("📎 Auto-asociados {} documento(s) al completar tarea '{}' (instancia '{}')",
                    urls.size(), task.getId(), instance.getId());
        } catch (Exception e) {
            log.error("Fallo en auto-asociación documental para tarea '{}': {}",
                    task != null ? task.getId() : "null", e.getMessage(), e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Set<String> collectFileUrls(TaskInstance task, Map<String, Object> formData) {
        Set<String> urls = new LinkedHashSet<>();
        if (formData != null) {
            formData.values().stream()
                    .filter(v -> v instanceof String && ((String) v).startsWith(FILE_URL_PREFIX))
                    .forEach(v -> urls.add((String) v));
        }
        if (task != null && task.getFormSubmissionId() != null) {
            FormSubmission submission = formSubmissionRepo.findById(task.getFormSubmissionId()).orElse(null);
            if (submission != null) {
                if (submission.getAttachmentUrls() != null) {
                    submission.getAttachmentUrls().stream()
                            .filter(u -> u != null && u.startsWith(FILE_URL_PREFIX))
                            .forEach(urls::add);
                }
                if (submission.getSignatureUrl() != null && submission.getSignatureUrl().startsWith(FILE_URL_PREFIX)) {
                    urls.add(submission.getSignatureUrl());
                }
            }
        }
        return urls;
    }

    private DocumentRepositoryEntry findEntry(String entryId) {
        DocumentRepositoryEntry entry = repo.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado: " + entryId));
        if (entry.isDeleted()) {
            throw new ResourceNotFoundException("Documento no encontrado: " + entryId);
        }
        return entry;
    }

    private void requireManage(DocumentRepositoryEntry entry, UserDetails actor) {
        if (!accessService.canManage(entry, actor)) {
            throw new AccessDeniedException("Sólo el propietario o un ADMIN puede administrar este documento");
        }
    }

    /**
     * Detecta el tipo documental por marcador de firma o por extensión del archivo.
     */
    private String detectDocumentType(String fileId, boolean isSignature) {
        if (isSignature) {
            return TYPE_SIGNATURE;
        }
        String ext = extractExtension(fileId);
        switch (ext) {
            case "pdf":
                return TYPE_PDF;
            case "doc":
            case "docx":
                return TYPE_WORD;
            case "xls":
            case "xlsx":
            case "csv":
                return TYPE_EXCEL;
            case "png":
            case "jpg":
            case "jpeg":
            case "webp":
            case "gif":
            case "svg":
                return TYPE_IMAGE;
            default:
                return TYPE_UNKNOWN;
        }
    }

    /**
     * Rellena metadata física (originalFilename, mimeType, fileSize) de forma
     * best-effort; si el archivo no existe no falla (lo deja en null/-1).
     */
    private void applyPhysicalMetadata(DocumentRepositoryEntry entry) {
        String fileId = entry.getFileId();
        entry.setOriginalFilename(extractOriginalFilename(fileId));
        if (fileId != null && storageProvider.exists(fileId)) {
            entry.setMimeType(storageProvider.contentType(fileId));
            long size = storageProvider.size(fileId);
            entry.setFileSize(size >= 0 ? size : null);
        }
    }

    private String extractExtension(String fileId) {
        String name = extractFilename(fileId);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Último segmento del path/URL (nombre de archivo tal cual se almacenó). */
    private String extractFilename(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return "documento";
        }
        String path = fileId;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * Nombre original aproximado: el upload guarda {UUID}_{nombre}; si detectamos
     * ese patrón removemos el prefijo UUID_. Si no, devolvemos el nombre tal cual.
     */
    private String extractOriginalFilename(String fileId) {
        String name = extractFilename(fileId);
        int underscore = name.indexOf('_');
        if (underscore == 36) { // longitud de un UUID estándar
            String candidate = name.substring(0, underscore);
            if (candidate.matches("[0-9a-fA-F-]{36}")) {
                return name.substring(underscore + 1);
            }
        }
        return name;
    }

    private Map<String, Object> metadata(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i] != null) {
                m.put(kv[i].toString(), kv[i + 1]);
            }
        }
        return m;
    }

    /**
     * Recurso resoluble para descarga, con nombre, content-type y tamaño
     * (contentLength = -1 si se desconoce) ya calculados.
     */
    public record DownloadResource(Resource resource, String filename, String contentType, long contentLength) {}
}
