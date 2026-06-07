package com.workflow.bpm.policy;

import com.workflow.bpm.policy.dto.CollaborativeMessageDTO;
import com.workflow.bpm.policy.dto.CollaborativeMessageDTO.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controlador WebSocket de alto rendimiento para colaboración en tiempo real
 * sobre diagramas BPMN.
 * <p>
 * Actúa como un despachador en memoria: las acciones livianas (DRAG, LOCK,
 * UNLOCK)
 * se reenvían inmediatamente sin tocar MongoDB ni servicios de negocio.
 * Solo ELEMENT_COMMIT dispara persistencia asíncrona.
 * <p>
 * Implementa filtro anti-eco: el emisor original nunca recibe su propio mensaje
 * de vuelta.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PolicyCollaborationController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PolicyCollaborationService collaborationService;

    /**
     * Punto de entrada para todos los mensajes colaborativos del diagramador.
     * Ruta STOMP: /app/policy/collaborate/{policyId}
     * <p>
     * Discrimina por ActionType:
     * - ELEMENT_DRAG / ELEMENT_LOCK / ELEMENT_UNLOCK → reenvío inmediato (sin DB).
     * - ELEMENT_COMMIT → persistencia asíncrona del XML completo.
     */
    @MessageMapping("/policy/collaborate/{policyId}")
    public void handleCollaborativeMessage(
            @DestinationVariable String policyId,
            @Payload CollaborativeMessageDTO message,
            Principal principal) {
        if (principal == null) {
            log.error("Msj Websocket rechazado: No hay usuario autenticado");
            return;
        }
        // Asegurar campos de contexto
        message.setSender(principal.getName());
        message.setPolicyId(policyId);
        message.setTimestamp(System.currentTimeMillis());

        ActionType action = message.getAction();
        if (action == null) {
            log.warn("Mensaje colaborativo sin acción recibido de '{}' en política '{}'",
                    principal.getName(), policyId);
            return;
        }

        switch (action) {
            case ELEMENT_DRAG:
            case ELEMENT_LOCK:
            case ELEMENT_UNLOCK:
                // ═══════════════════════════════════════════════════════════
                // RUTA RÁPIDA: reenvío inmediato en memoria.
                // Sin interacción con MongoDB ni servicios pesados.
                // ═══════════════════════════════════════════════════════════
                if (action == ActionType.ELEMENT_LOCK) {
                    collaborationService.trackLock(principal.getName(), policyId, message.getElementId());
                }
                if (action == ActionType.ELEMENT_UNLOCK) {
                    collaborationService.removeLock(principal.getName(), message.getElementId());
                }

                // Limpiar XML para no enviar payload masivo en acciones livianas
                message.setBpmnXml(null);

                broadcastExcludingSender(policyId, message, principal.getName());
                log.debug("⚡ {} reenviado en memoria — usuario: '{}', elemento: '{}', política: '{}'",
                        action, principal.getName(), message.getElementId(), policyId);
                break;

            case ELEMENT_COMMIT:
                // ═══════════════════════════════════════════════════════════
                // RUTA PESADA: aceptar bpmnXml y persistir de forma asíncrona.
                // El hilo del WebSocket se libera de inmediato.
                // ═══════════════════════════════════════════════════════════
                // Defensa contra payloads corruptos (elementId undefined/vacío
                // o bpmnXml sin estructura mínima): no retransmitir ni persistir.
                if (!isValidCommit(message)) {
                    log.warn("ELEMENT_COMMIT descartado por payload corrupto — usuario: '{}', política: '{}', elementId: '{}'",
                            principal.getName(), policyId, message.getElementId());
                    return;
                }

                // ─── Optimistic Locking: validar versión ANTES de persistir ──
                PolicyCollaborationService.CommitDecision decision =
                        collaborationService.validateAndBump(policyId, message.getVersion());

                if (!decision.accepted()) {
                    // Condición de carrera: el usuario envió un estado obsoleto.
                    // Rechazar el XML viejo y forzar al infractor a recargar.
                    CollaborativeMessageDTO refresh = CollaborativeMessageDTO.builder()
                            .action(ActionType.CREATION_DESYNC_REFRESH)
                            .policyId(policyId)
                            .sender(principal.getName())
                            .version(decision.serverVersion())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    messagingTemplate.convertAndSend("/topic/policy/" + policyId, refresh);
                    log.warn("⛔ ELEMENT_COMMIT obsoleto rechazado — usuario: '{}', política: '{}', " +
                                    "versión enviada: {}, versión servidor: {}",
                            principal.getName(), policyId, message.getVersion(), decision.serverVersion());
                    return;
                }

                // Aceptado: sellar la nueva versión autoritativa en el payload.
                message.setVersion(decision.serverVersion());

                collaborationService.commitBpmnXmlAsync(
                        policyId, message.getBpmnXml(), decision.serverVersion(), principal.getName());

                // Retransmitir el XML completo + nueva versión para que TODOS los
                // colaboradores (incluido el emisor) sincronicen su diagrama y versión.
                broadcastExcludingSender(policyId, message, principal.getName());
                message.setBpmnXml(null);
                log.info("💾 ELEMENT_COMMIT aceptado v{} — usuario: '{}', política: '{}'",
                        decision.serverVersion(), principal.getName(), policyId);
                break;

            default:
                log.warn("Acción desconocida '{}' recibida de '{}'", action, principal.getName());
        }
    }

    // ─── Validación Defensiva de Commit ─────────────────────────────────
    /**
     * Valida que un mensaje ELEMENT_COMMIT esté bien formado antes de
     * retransmitirlo o encolarlo para persistencia asíncrona.
     * <p>
     * Rechaza payloads corruptos provenientes de bugs del cliente:
     * <ul>
     *   <li>{@code elementId} nulo, vacío o con el literal "undefined".</li>
     *   <li>{@code bpmnXml} nulo, vacío o sin estructura BPMN mínima
     *       (previsualizaciones transitorias o fragmentos).</li>
     * </ul>
     *
     * @return {@code true} si el commit es válido y puede procesarse.
     */
    private boolean isValidCommit(CollaborativeMessageDTO message) {
        String elementId = message.getElementId();
        if (elementId == null || elementId.isBlank() || "undefined".equalsIgnoreCase(elementId.trim())) {
            return false;
        }
        String xml = message.getBpmnXml();
        if (xml == null || xml.isBlank()) {
            return false;
        }
        String normalized = xml.trim();
        return normalized.contains("definitions") && normalized.contains("<");
    }

    // ─── Difusión al Topic ──────────────────────────────────────────────
    /**
     * Publica el mensaje en el topic /topic/policy/{policyId} al que el frontend
     * está suscrito.
     * <p>
     * El SimpleBroker no permite excluir al emisor de un topic compartido, por lo
     * que el filtro anti-eco se delega al cliente: cada colaborador debe ignorar
     * los mensajes cuyo {@code sender} coincida con su propio usuario (campo
     * disponible en {@link CollaborativeMessageDTO}).
     */
    private void broadcastExcludingSender(String policyId, CollaborativeMessageDTO message, String senderName) {
        messagingTemplate.convertAndSend("/topic/policy/" + policyId, message);
    }
}