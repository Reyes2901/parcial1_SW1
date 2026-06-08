package com.workflow.bpm.workflow.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {
    private String nodeId;
    private String nodeLabel;
    private String action;          // NODE_STARTED | NODE_COMPLETED | REJECTED | CANCELLED
    private String userId;          // "system" si lo hizo el motor
    private Instant timestamp;
    private Long durationMinutes;   // tiempo que tardó este paso
    private String transitionTaken; // id de la transición elegida
    private Map<String, Object> formData;
    
    // Constantes para acciones
    public static final String ACTION_NODE_STARTED = "NODE_STARTED";
    public static final String ACTION_NODE_COMPLETED = "NODE_COMPLETED";
    public static final String ACTION_REJECTED = "REJECTED";
    public static final String ACTION_CANCELLED = "CANCELLED";
    public static final String ACTION_SYSTEM = "SYSTEM";

    // Constantes para eventos del Repositorio Documental
    public static final String ACTION_DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    public static final String ACTION_DOCUMENT_DELETED = "DOCUMENT_DELETED";
    public static final String ACTION_DOCUMENT_DOWNLOADED = "DOCUMENT_DOWNLOADED";
    public static final String ACTION_DOCUMENT_PERMISSION_GRANTED = "DOCUMENT_PERMISSION_GRANTED";
    public static final String ACTION_DOCUMENT_PERMISSION_REVOKED = "DOCUMENT_PERMISSION_REVOKED";
    public static final String ACTION_DOCUMENT_SIGNED = "DOCUMENT_SIGNED";
}