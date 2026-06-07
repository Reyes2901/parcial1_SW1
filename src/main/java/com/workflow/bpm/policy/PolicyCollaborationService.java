package com.workflow.bpm.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Servicio de colaboración en tiempo real para diagramas BPMN.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li>Rastreo en memoria de locks por usuario/elemento (ConcurrentHashMap)</li>
 *   <li>Persistencia asíncrona del XML completo en ELEMENT_COMMIT</li>
 *   <li>Liberación de locks huérfanos por desconexión abrupta</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyCollaborationService {

    private final PolicyService policyService;

    /**
     * Mapa concurrente de locks activos.
     * Clave: userId (Principal.getName())
     * Valor: Set de LockEntry con policyId y elementId
     */
    private final Map<String, Set<LockEntry>> activeLocks = new ConcurrentHashMap<>();

    /**
     * Caché autoritativa de versiones de diagrama por política (optimistic locking).
     * Clave: policyId. Valor: versión secuencial actual.
     * Se inicializa de forma perezosa desde la BD (campo {@code diagramVersion}).
     */
    private final Map<String, Long> diagramVersions = new ConcurrentHashMap<>();

    // ─── Gestión de Locks en Memoria ─────────────────────────────────────

    /**
     * Registra un lock de un usuario sobre un elemento específico en una política.
     */
    public void trackLock(String userId, String policyId, String elementId) {
        activeLocks
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(new LockEntry(policyId, elementId));

        log.debug("🔒 Lock registrado — usuario: '{}', policy: '{}', elemento: '{}'",
                userId, policyId, elementId);
    }

    /**
     * Remueve un lock específico de un usuario sobre un elemento.
     */
    public void removeLock(String userId, String elementId) {
        Set<LockEntry> userLocks = activeLocks.get(userId);
        if (userLocks != null) {
            userLocks.removeIf(entry -> entry.elementId().equals(elementId));
            if (userLocks.isEmpty()) {
                activeLocks.remove(userId);
            }
            log.debug("🔓 Lock removido — usuario: '{}', elemento: '{}'", userId, elementId);
        }
    }

    /**
     * Obtiene y remueve todos los locks de un usuario (para limpieza por desconexión).
     *
     * @return Set inmutable de LockEntry del usuario, o vacío si no tenía locks.
     */
    public Set<LockEntry> removeAllLocks(String userId) {
        Set<LockEntry> locks = activeLocks.remove(userId);
        if (locks == null || locks.isEmpty()) {
            return Collections.emptySet();
        }
        log.info("🧹 {} locks removidos para usuario desconectado: '{}'", locks.size(), userId);
        return Set.copyOf(locks);
    }

    // ─── Optimistic Locking (Control de Versiones) ───────────────────────

    /**
     * Valida de forma atómica la versión de un commit entrante contra la versión
     * autoritativa del servidor y, si procede, la incrementa.
     * <p>
     * La operación completa (leer versión actual + comparar + incrementar) se
     * ejecuta dentro de {@link ConcurrentHashMap#compute} por clave de política,
     * garantizando atomicidad y eliminando la condición de carrera donde dos
     * usuarios commitean simultáneamente sobre la misma versión.
     *
     * @param policyId        política editada.
     * @param incomingVersion versión sobre la que el cliente realizó sus cambios.
     *                        {@code null} se trata como "en sincronía" (compat.).
     * @return decisión: aceptado (con la nueva versión) o rechazado (con la
     *         versión autoritativa actual para forzar refresco).
     */
    public CommitDecision validateAndBump(String policyId, Long incomingVersion) {
        final CommitDecision[] result = new CommitDecision[1];
        diagramVersions.compute(policyId, (key, current) -> {
            long curr = (current != null) ? current : loadInitialVersion(policyId);
            long incoming = (incomingVersion != null) ? incomingVersion : curr;

            if (incoming < curr) {
                // Commit obsoleto: el cliente envió un estado viejo. Rechazar.
                result[0] = new CommitDecision(false, curr);
                return curr; // versión inalterada
            }

            long next = Math.max(incoming, curr) + 1;
            result[0] = new CommitDecision(true, next);
            return next;
        });
        return result[0];
    }

    /**
     * Carga la versión inicial del diagrama desde la BD (única lectura por
     * política, al primer commit de la sesión). Si no existe, arranca en 0.
     */
    private long loadInitialVersion(String policyId) {
        try {
            ProcessDefinition policy = policyService.findById(policyId);
            return policy.getDiagramVersion() != null ? policy.getDiagramVersion() : 0L;
        } catch (Exception e) {
            log.warn("No se pudo cargar diagramVersion inicial para policy '{}': {}",
                    policyId, e.getMessage());
            return 0L;
        }
    }

    // ─── Persistencia Asíncrona ──────────────────────────────────────────

    /**
     * Persiste el XML BPMN completo y la nueva versión en MongoDB de forma asíncrona.
     * Se ejecuta en un hilo separado del pool @Async para no bloquear el hilo del WebSocket.
     */
    @Async
    public void commitBpmnXmlAsync(String policyId, String bpmnXml, Long newVersion, String userId) {
        try {
            log.info("💾 [ASYNC] Iniciando persistencia de BPMN XML v{} — policy: '{}', usuario: '{}'",
                    newVersion, policyId, userId);

            ProcessDefinition policy = policyService.findById(policyId);
            policy.setBpmnXml(bpmnXml);
            policy.setDiagramVersion(newVersion);
            policy.setUpdatedAt(Instant.now());
            policyService.save(policy);

            log.info("✅ [ASYNC] BPMN XML v{} persistido exitosamente — policy: '{}', usuario: '{}'",
                    newVersion, policyId, userId);
        } catch (Exception e) {
            log.error("❌ [ASYNC] Error al persistir BPMN XML — policy: '{}', usuario: '{}': {}",
                    policyId, userId, e.getMessage(), e);
        }
    }

    /**
     * Decisión del validador de optimistic locking.
     *
     * @param accepted      {@code true} si el commit puede persistirse y difundirse.
     * @param serverVersion si aceptado, la NUEVA versión autoritativa; si
     *                     rechazado, la versión autoritativa ACTUAL del servidor.
     */
    public record CommitDecision(boolean accepted, long serverVersion) {}

    // ─── Record para Lock Entry ──────────────────────────────────────────

    /**
     * Registro inmutable de un lock activo, asociando una política con un elemento.
     */
    public record LockEntry(String policyId, String elementId) {}
}
