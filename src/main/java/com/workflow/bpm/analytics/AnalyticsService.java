package com.workflow.bpm.analytics;

import com.workflow.bpm.analytics.dto.*;
import com.workflow.bpm.department.Department;
import com.workflow.bpm.department.DepartmentRepository;
import com.workflow.bpm.policy.PolicyRepository;
import com.workflow.bpm.policy.ProcessDefinition;
import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import com.workflow.bpm.workflow.document.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepo;
    private final PolicyRepository policyRepo;
    private final TaskInstanceRepository taskRepo;
    private final ProcessInstanceRepository instanceRepo;
    private final DepartmentRepository departmentRepo;

    // -------------------------------------------------------
    // Métricas completas de una política — para el admin
    // -------------------------------------------------------
    public PolicyStats getPolicyStats(String definitionId) {
        ProcessDefinition def = policyRepo.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));

        // Obtener estadísticas por nodo desde MongoDB
        List<Document> nodeDocuments = analyticsRepo.getNodeStatsByDefinition(definitionId);

        List<NodeStats> nodeStatsList = nodeDocuments.stream()
                .map(doc -> {
                    NodeStats stats = NodeStats.builder()
                            .nodeId(doc.getString("_id"))
                            .nodeLabel(doc.getString("nodeLabel"))
                            .laneId(doc.get("_id").toString())
                            .totalTasks(getLong(doc, "totalTasks"))
                            .avgDurationMinutes(getDouble(doc, "avgDurationMinutes"))
                            .minDurationMinutes(getDouble(doc, "minDurationMinutes"))
                            .maxDurationMinutes(getDouble(doc, "maxDurationMinutes"))
                            .overdueCount(getLong(doc, "overdueCount"))
                            .overdueRatePct(getDouble(doc, "overdueRatePct"))
                            .build();
                    
                    // Calcular ratio real vs estimado
                    stats.setEstimatedVsRealRatio(calcRatio(def, doc));
                    
                    // Buscar nombre del lane
                    def.findLaneById(stats.getLaneId())
                            .ifPresent(lane -> stats.setLaneName(lane.getName()));
                    
                    return stats;
                })
                .collect(Collectors.toList());

        // Identificar el nodo más lento (el cuello de botella)
        NodeStats bottleneck = nodeStatsList.stream()
                .max(Comparator.comparingDouble(NodeStats::getAvgDurationMinutes))
                .orElse(null);

        // Contadores de instancias
        long total = instanceRepo.countByDefinitionId(definitionId);
        long completed = instanceRepo.countByDefinitionIdAndStatus(definitionId, "COMPLETED");
        long rejected = instanceRepo.countByDefinitionIdAndStatus(definitionId, "REJECTED");
        long active = instanceRepo.countByDefinitionIdAndStatus(definitionId, "IN_PROGRESS");

        double completionRate = total > 0 ? (completed * 100.0 / total) : 0.0;

        // Duración promedio total del trámite
        double avgTotalHours = calcAvgTotalDuration(definitionId);

        return PolicyStats.builder()
                .definitionId(definitionId)
                .definitionName(def.getName())
                .totalInstances(total)
                .completedInstances(completed)
                .rejectedInstances(rejected)
                .activeInstances(active)
                .completionRatePct(Math.round(completionRate * 10.0) / 10.0)
                .avgTotalDurationHours(Math.round(avgTotalHours * 10.0) / 10.0)
                .nodeStats(nodeStatsList)
                .bottleneckNodeId(bottleneck != null ? bottleneck.getNodeId() : null)
                .bottleneckNodeLabel(bottleneck != null ? bottleneck.getNodeLabel() : null)
                .build();
    }

    // -------------------------------------------------------
    // Dashboard ejecutivo — resumen global para el admin
    // -------------------------------------------------------
    public DashboardSummary getDashboard() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        long activeInstances = instanceRepo.countByStatus("IN_PROGRESS");
        long completedToday = analyticsRepo.getCompletedToday();
        long rejectedToday = analyticsRepo.getRejectedToday();
        long overdueTasks = taskRepo.countByStatusInAndDueAtBefore(
                List.of("PENDING", "IN_PROGRESS"), new Date());

        // Tasa de completación global
        long totalAll = instanceRepo.count();
        long completedAll = instanceRepo.countByStatus("COMPLETED");
        double globalRate = totalAll > 0 ? (completedAll * 100.0 / totalAll) : 0.0;

        // Tiempo promedio de resolución
        double avgResolutionHours = analyticsRepo.getAvgResolutionHours();

        // Cuellos de botella activos
        List<BottleneckReport> bottlenecks = analyticsRepo.getActiveBottlenecks()
                .stream()
                .limit(10)
                .map(doc -> BottleneckReport.builder()
                        .taskId(doc.get("_id").toString())
                        .instanceId(doc.getString("instanceId"))
                        .nodeLabel(doc.getString("nodeLabel"))
                        .laneName(doc.getString("laneId"))
                        .assigneeId(doc.getString("assigneeId"))
                        .overdueMinutes(getLong(doc, "overdueMinutes"))
                        .priority(doc.getString("priority"))
                        .dueAt(doc.getDate("dueAt") != null
                                ? doc.getDate("dueAt").toInstant() : null)
                        .build())
                .collect(Collectors.toList());

        // Top 3 políticas
        List<PolicyUsage> topPolicies = analyticsRepo.getTopPoliciesByUsage(3)
                .stream()
                .map(doc -> PolicyUsage.builder()
                        .definitionId(doc.get("_id").toString())
                        .definitionName(doc.getString("definitionName"))
                        .totalInstances(getLong(doc, "totalInstances"))
                        .activeInstances(getLong(doc, "activeInstances"))
                        .build())
                .collect(Collectors.toList());

        // Carga por departamento (agrupado por departmentId real)
        Map<String, String> deptNameCache = departmentRepo.findAll().stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        List<DepartmentLoad> deptLoad = analyticsRepo.getDepartmentLoad()
                .stream()
                .map(doc -> {
                    String deptId = doc.get("_id") != null ? doc.get("_id").toString() : null;
                    String deptName = deptId != null ? deptNameCache.getOrDefault(deptId, deptId) : "Unknown";
                    return DepartmentLoad.builder()
                            .departmentId(deptId)
                            .departmentName(deptName)
                            .laneId(deptId)          // backward compat
                            .laneName(deptName)      // backward compat
                            .pendingTasks(getLong(doc, "pendingTasks"))
                            .inProgressTasks(getLong(doc, "inProgressTasks"))
                            .completedToday(getLong(doc, "completedToday"))
                            .build();
                })
                .collect(Collectors.toList());

        return DashboardSummary.builder()
                .totalActiveInstances(activeInstances)
                .totalCompletedToday(completedToday)
                .totalRejectedToday(rejectedToday)
                .totalOverdueTasks(overdueTasks)
                .globalCompletionRatePct(Math.round(globalRate * 10.0) / 10.0)
                .avgResolutionHours(Math.round(avgResolutionHours * 10.0) / 10.0)
                .topPolicies(topPolicies)
                .activeBottlenecks(bottlenecks)
                .departmentLoad(deptLoad)
                .build();
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------
    private double calcRatio(ProcessDefinition def, Document doc) {
        String nodeId = doc.getString("_id");
        return def.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId) && n.getEstimatedDurationHours() != null)
                .findFirst()
                .map(n -> {
                    double estimated = n.getEstimatedDurationHours() * 60.0;
                    double real = getDouble(doc, "avgDurationMinutes");
                    return estimated > 0 ? Math.round((real / estimated) * 100.0) / 100.0 : 0.0;
                })
                .orElse(0.0);
    }

    private double calcAvgTotalDuration(String definitionId) {
        return instanceRepo.findByDefinitionIdAndStatus(definitionId, "COMPLETED")
                .stream()
                .filter(i -> i.getStartedAt() != null && i.getCompletedAt() != null)
                .mapToLong(i -> ChronoUnit.MINUTES.between(i.getStartedAt(), i.getCompletedAt()))
                .average()
                .orElse(0.0) / 60.0;
    }

    private double getDouble(Document doc, String key) {
        Object v = doc.get(key);
        if (v == null) return 0.0;
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private long getLong(Document doc, String key) {
        Object v = doc.get(key);
        if (v == null) return 0L;
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }
    /**
         * Eficiencia individual del funcionario
         */
        public Map<String, Object> getUserPerformance(String userId) {
        List<TaskInstance> completadas = taskRepo
                .findByAssigneeIdAndStatusIn(userId, List.of("COMPLETED"));

        if (completadas.isEmpty()) {
                return Map.of(
                        "userId", userId,
                        "message", "Sin tareas completadas",
                        "totalCompleted", 0,
                        "avgDurationMinutes", 0.0,
                        "onTimeRatePct", 0.0,
                        "currentPending", taskRepo.countByAssigneeIdAndStatus(userId, "PENDING"),
                        "currentInProgress", taskRepo.countByAssigneeIdAndStatus(userId, "IN_PROGRESS")
                );
        }

        double avgMinutes = completadas.stream()
                .filter(t -> t.getDurationMinutes() != null)
                .mapToLong(TaskInstance::getDurationMinutes)
                .average().orElse(0.0);

        long onTime = completadas.stream()
                .filter(t -> t.getCompletedAt() != null && t.getDueAt() != null
                        && !t.getCompletedAt().isAfter(t.getDueAt()))
                .count();

        double onTimeRate = completadas.size() > 0
                ? (onTime * 100.0 / completadas.size()) : 0.0;

        long pendingNow = taskRepo.countByAssigneeIdAndStatus(userId, "PENDING");
        long inProgNow = taskRepo.countByAssigneeIdAndStatus(userId, "IN_PROGRESS");

        return Map.of(
                "userId", userId,
                "totalCompleted", completadas.size(),
                "avgDurationMinutes", Math.round(avgMinutes * 10.0) / 10.0,
                "onTimeRatePct", Math.round(onTimeRate * 10.0) / 10.0,
                "currentPending", pendingNow,
                "currentInProgress", inProgNow
        );
        }
}