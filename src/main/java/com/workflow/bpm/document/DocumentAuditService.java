package com.workflow.bpm.document;

import com.workflow.bpm.workflow.document.AuditEntry;
import com.workflow.bpm.workflow.document.ProcessInstance;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Registra eventos documentales reutilizando el sistema de auditoría existente:
 * añade {@link AuditEntry} al {@code auditLog} embebido de la {@link ProcessInstance}.
 * <p>
 * No crea un sistema nuevo: los eventos aparecen en
 * {@code GET /api/workflow/instances/{id}/history}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAuditService {

    private final ProcessInstanceRepository instanceRepo;

    /**
     * Añade un evento documental a la instancia indicada. Si la instancia no
     * existe o no se proporciona id, registra un warning y no falla (los eventos
     * documentales nunca deben romper el flujo principal).
     */
    public void record(String instanceId, String action, String userId, Map<String, Object> metadata) {
        if (instanceId == null || instanceId.isBlank()) {
            log.warn("Evento documental '{}' sin instanceId; no se persiste auditoría (usuario: '{}')",
                    action, userId);
            return;
        }
        try {
            ProcessInstance instance = instanceRepo.findById(instanceId).orElse(null);
            if (instance == null) {
                log.warn("Evento documental '{}' para instancia inexistente '{}'; auditoría omitida",
                        action, instanceId);
                return;
            }
            instance.addAuditEntry(AuditEntry.builder()
                    .nodeId(instance.getCurrentNodeId())
                    .nodeLabel(instance.getCurrentNodeLabel())
                    .action(action)
                    .userId(userId != null ? userId : "system")
                    .timestamp(Instant.now())
                    .formData(metadata)
                    .build());
            instanceRepo.save(instance);
        } catch (Exception e) {
            log.error("No se pudo registrar auditoría documental '{}' en instancia '{}': {}",
                    action, instanceId, e.getMessage(), e);
        }
    }
}
