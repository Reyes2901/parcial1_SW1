package com.workflow.bpm.analytics;

import com.workflow.bpm.analytics.dto.BottleneckReport;
import com.workflow.bpm.analytics.dto.DashboardSummary;
import com.workflow.bpm.analytics.dto.DepartmentLoad;
import com.workflow.bpm.analytics.dto.PolicyStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Dashboard ejecutivo — resumen global.
     * Angular lo usa para la pantalla principal del admin.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboard() {
        log.info("📊 Solicitando dashboard...");
        try {
            DashboardSummary summary = analyticsService.getDashboard();
            log.info("✅ Dashboard generado: {} instancias activas", summary.getTotalActiveInstances());
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("❌ Error en dashboard: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "cause", e.getCause() != null ? e.getCause().getMessage() : "null"
            ));
        }
    }

    /**
     * Estadísticas detalladas de una política.
     * Incluye tiempos por nodo y el nodo más lento.
     */
    @GetMapping("/policies/{id}/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PolicyStats> getPolicyStats(@PathVariable String id) {
        log.info("📊 Solicitando stats de política: {}", id);
        return ResponseEntity.ok(analyticsService.getPolicyStats(id));
    }

    /**
     * Lista de cuellos de botella activos en tiempo real.
     * Tareas PENDING o IN_PROGRESS con dueAt vencido.
     */
    @GetMapping("/bottlenecks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BottleneckReport>> getBottlenecks() {
        log.info("📊 Solicitando cuellos de botella activos");
        return ResponseEntity.ok(analyticsService.getDashboard().getActiveBottlenecks());
    }

    /**
     * Carga de trabajo por departamento/lane.
     * Muestra qué departamentos están sobrecargados.
     */
    @GetMapping("/department-load")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DepartmentLoad>> getDepartmentLoad() {
        log.info("📊 Solicitando carga por departamento");
        return ResponseEntity.ok(analyticsService.getDashboard().getDepartmentLoad());
    }

    /**
     * Eficiencia de un funcionario específico.
     * Útil para reportes de desempeño.
     */
    @GetMapping("/users/{userId}/performance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserPerformance(@PathVariable String userId) {
        log.info("📊 Solicitando rendimiento de usuario: {}", userId);
        return ResponseEntity.ok(analyticsService.getUserPerformance(userId));
    }
}