package com.workflow.bpm.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import org.bson.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private final MongoTemplate mongo;

    /**
     * QUERY 1 — Tiempos promedio por nodo para una política.
     */
    public List<Document> getNodeStatsByDefinition(String definitionId) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("definitionId").is(definitionId)
                .and("status").is("COMPLETED")
                .and("durationMinutes").ne(null)),
            Aggregation.group("nodeId")
                .first("nodeLabel").as("nodeLabel")
                .first("laneId").as("laneId")
                .count().as("totalTasks")
                .avg("durationMinutes").as("avgDurationMinutes")
                .min("durationMinutes").as("minDurationMinutes")
                .max("durationMinutes").as("maxDurationMinutes")
                .sum(ConditionalOperators
                    .when(Criteria.where("completedAt").gt("dueAt"))
                    .then(1).otherwise(0))
                    .as("overdueCount"),
            Aggregation.project("nodeLabel", "laneId", "totalTasks", 
                    "avgDurationMinutes", "minDurationMinutes", 
                    "maxDurationMinutes", "overdueCount")
                .andExpression("overdueCount * 100.0 / totalTasks").as("overdueRatePct"),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "avgDurationMinutes"))
        );

        return mongo.aggregate(agg, "task_instances", Document.class).getMappedResults();
    }

    /**
     * QUERY 2 — Resumen de instancias por política.
     */
    public List<Document> getInstanceSummaryByDefinition(String definitionId) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("definitionId").is(definitionId)),
            Aggregation.group("status").count().as("count"),
            Aggregation.project("count").and("_id").as("status")
        );

        return mongo.aggregate(agg, "process_instances", Document.class).getMappedResults();
    }

    /**
     * QUERY 3 — Tareas vencidas activas (cuellos de botella en tiempo real).
     */
    public List<Document> getActiveBottlenecks() {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("status").in("PENDING", "IN_PROGRESS")
                    .and("dueAt").lt(new Date())),
            
            Aggregation.project("instanceId", "nodeLabel", "laneId", 
                    "assigneeId", "priority", "dueAt")
                .and("_id").as("taskId")
                .andExpression("(new Date().getTime() - dueAt.getTime()) / 60000")  // 👈 CORRECCIÓN
                .as("overdueMinutes"),
            
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "overdueMinutes"))
            );
        return mongo.aggregate(agg, "task_instances", Document.class).getMappedResults();
    }
    /**
     * QUERY 4 — Carga de trabajo por departamento (laneId).
     */
    public List<Document> getDepartmentLoad() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(new Criteria().orOperator(
                Criteria.where("status").in("PENDING", "IN_PROGRESS"),
                Criteria.where("status").is("COMPLETED").and("completedAt").gte(Date.from(startOfDay))
            )),
            Aggregation.group("departmentId")
                .sum(ConditionalOperators.when(Criteria.where("status").is("PENDING")).then(1).otherwise(0)).as("pendingTasks")
                .sum(ConditionalOperators.when(Criteria.where("status").is("IN_PROGRESS")).then(1).otherwise(0)).as("inProgressTasks")
                .sum(ConditionalOperators.when(Criteria.where("status").is("COMPLETED")).then(1).otherwise(0)).as("completedToday"),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "pendingTasks"))
        );

        return mongo.aggregate(agg, "task_instances", Document.class).getMappedResults();
    }

    /**
     * QUERY 5 — Top políticas por uso.
     */
    public List<Document> getTopPoliciesByUsage(int limit) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.group("definitionId")
                .first("definitionName").as("definitionName")
                .count().as("totalInstances")
                .sum(ConditionalOperators.when(Criteria.where("status").is("IN_PROGRESS")).then(1).otherwise(0)).as("activeInstances"),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalInstances")),
            Aggregation.limit(limit)
        );

        return mongo.aggregate(agg, "process_instances", Document.class).getMappedResults();
    }

    /**
     * QUERY 6 - Total completados hoy
     */
    public long getCompletedToday() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return mongo.count(
            new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("status").is("COMPLETED").and("completedAt").gte(Date.from(startOfDay))
            ), "process_instances"
        );
    }

    /**
     * QUERY 7 - Total rechazados hoy
     */
    public long getRejectedToday() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return mongo.count(
            new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("status").is("REJECTED").and("completedAt").gte(Date.from(startOfDay))
            ), "process_instances"
        );
    }

    /**
     * QUERY 8 - Tiempo promedio de resolución global
     */
    public double getAvgResolutionHours() {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("status").is("COMPLETED").and("durationMinutes").ne(null)),
            Aggregation.group().avg("durationMinutes").as("avgMinutes")
        );
        Document result = mongo.aggregate(agg, "task_instances", Document.class).getUniqueMappedResult();
        if (result != null && result.get("avgMinutes") != null) {
            return result.getDouble("avgMinutes") / 60.0;
        }
        return 0.0;
    }
}