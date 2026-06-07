package com.workflow.bpm.policy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de mensajería estructurada para colaboración en tiempo real sobre diagramas BPMN.
 * <p>
 * Discrimina entre acciones livianas (DRAG, LOCK, UNLOCK) que se retransmiten
 * sin tocar la base de datos, y ELEMENT_COMMIT que incluye el XML completo
 * para persistencia asíncrona.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborativeMessageDTO {

    /**
     * Tipo de acción ejecutada por el usuario en el diagramador.
     */
    private ActionType action;

    /**
     * ID de la política (proceso) que se está editando colaborativamente.
     */
    private String policyId;

    /**
     * Identificador del usuario que originó este mensaje.
     * Se usa como filtro anti-eco para evitar que reciba su propio broadcast.
     */
    private String sender;

    /**
     * ID del elemento BPMN afectado (nodo, transición, lane, etc.).
     */
    private String elementId;

    /**
     * Posición y dimensiones del elemento. Solo relevante para ELEMENT_DRAG.
     */
    private Geometry geometry;

    /**
     * String XML completo del diagrama BPMN.
     * SOLO se procesará cuando la acción sea ELEMENT_COMMIT.
     */
    private String bpmnXml;

    /**
     * Versión secuencial del diagrama (optimistic locking).
     * <p>
     * En un {@code ELEMENT_COMMIT} entrante: versión sobre la que el cliente
     * realizó sus cambios. El servidor la valida contra la versión autoritativa.
     * <p>
     * En un broadcast saliente ({@code ELEMENT_COMMIT} aceptado o
     * {@code CREATION_DESYNC_REFRESH}): nueva versión autoritativa del servidor.
     */
    private Long version;

    /**
     * Timestamp del mensaje en milisegundos (epoch).
     */
    private long timestamp;

    // ─── Enum de acciones ───────────────────────────────────────────────

    public enum ActionType {
        /** Movimiento continuo de un elemento (arrastrar). Sin persistencia. */
        ELEMENT_DRAG,
        /** Commit final del XML completo. Se persiste de forma asíncrona. */
        ELEMENT_COMMIT,
        /** Bloqueo de un elemento para edición exclusiva. */
        ELEMENT_LOCK,
        /** Liberación de un elemento previamente bloqueado. */
        ELEMENT_UNLOCK,
        /**
         * Rechazo de commit obsoleto: el servidor detectó una condición de carrera
         * (la versión enviada es menor a la autoritativa). Obliga al frontend
         * infractor a recargar el XML real del servidor.
         */
        CREATION_DESYNC_REFRESH
    }

    // ─── Subclase de geometría ──────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Geometry {
        private double x;
        private double y;
        private double width;
        private double height;
    }
}
