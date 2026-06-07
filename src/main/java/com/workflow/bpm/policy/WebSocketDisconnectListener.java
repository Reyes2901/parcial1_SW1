package com.workflow.bpm.policy;

import com.workflow.bpm.policy.dto.CollaborativeMessageDTO;
import com.workflow.bpm.policy.dto.CollaborativeMessageDTO.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;

/**
 * Listener de eventos de desconexión WebSocket (SessionDisconnectEvent).
 * <p>
 * Detecta desconexiones abruptas (cierre de pestaña, caída de internet, timeout)
 * y libera automáticamente todos los ELEMENT_LOCK que el usuario tenía activos,
 * evitando que el diagrama quede inaccesible para los demás colaboradores.
 * <p>
 * Flujo:
 * <ol>
 *   <li>Spring dispara SessionDisconnectEvent cuando la sesión WS se cierra.</li>
 *   <li>Se obtiene el Principal del usuario desconectado.</li>
 *   <li>Se consultan y remueven todos sus locks en PolicyCollaborationService.</li>
 *   <li>Por cada lock, se genera un mensaje ELEMENT_UNLOCK de contingencia
 *       y se transmite al topic /topic/policy/{policyId}.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDisconnectListener {

    private final PolicyCollaborationService collaborationService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        var principal = event.getUser();
        if (principal == null) {
            log.debug("Desconexión WebSocket de sesión sin Principal (no autenticada). SessionId: {}",
                    event.getSessionId());
            return;
        }

        String userId = principal.getName();
        log.info("🔌 Desconexión WebSocket detectada — usuario: '{}', sessionId: '{}'",
                userId, event.getSessionId());

        // Obtener y limpiar todos los locks activos del usuario desconectado
        Set<PolicyCollaborationService.LockEntry> orphanedLocks =
                collaborationService.removeAllLocks(userId);

        if (orphanedLocks.isEmpty()) {
            log.debug("Usuario '{}' no tenía locks activos al desconectarse.", userId);
            return;
        }

        // Generar mensajes ELEMENT_UNLOCK de contingencia para cada lock huérfano
        for (PolicyCollaborationService.LockEntry lock : orphanedLocks) {
            CollaborativeMessageDTO unlockMessage = CollaborativeMessageDTO.builder()
                    .action(ActionType.ELEMENT_UNLOCK)
                    .policyId(lock.policyId())
                    .sender(userId)
                    .elementId(lock.elementId())
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Broadcast a todos los suscriptores (el emisor ya está desconectado,
            // por lo que no hay riesgo de eco)
            String destination = "/topic/policy/" + lock.policyId();
            messagingTemplate.convertAndSend(destination, unlockMessage);

            log.warn("🔓 UNLOCK de contingencia emitido — policy: '{}', elemento: '{}', " +
                            "usuario desconectado: '{}'",
                    lock.policyId(), lock.elementId(), userId);
        }

        log.info("✅ {} locks huérfanos liberados para usuario '{}'", orphanedLocks.size(), userId);
    }
}
