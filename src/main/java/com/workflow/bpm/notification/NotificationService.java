package com.workflow.bpm.notification;

import com.workflow.bpm.notification.dto.BottleneckAlert;
import com.workflow.bpm.notification.dto.InstanceStatusUpdate;
import com.workflow.bpm.notification.dto.TaskNotification;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.workflow.document.ProcessInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messaging;

    /**
     * Nueva tarea asignada al funcionario.
     * Canal privado: /user/{assigneeId}/queue/tasks
     * Solo ese funcionario la recibe.
     */
    public void notifyNewTask(String assigneeId, TaskInstance task) {
        TaskNotification event = TaskNotification.builder()
                .type(TaskNotification.TYPE_NEW_TASK)
                .taskId(task.getId())
                .nodeLabel(task.getNodeLabel())
                .clientName(task.getClientName())
                .priority(task.getPriority())
                .instanceId(task.getInstanceId())
                .dueAt(task.getDueAt())
                .timestamp(Instant.now())
                .build();

        messaging.convertAndSendToUser(assigneeId, "/queue/tasks", event);
        log.debug("WS→ funcionario '{}': nueva tarea '{}'", assigneeId, task.getNodeLabel());
    }

    /**
     * El trámite avanzó de paso.
     * Canal privado: /user/{clientId}/queue/instance-status
     * El cliente ve en su pantalla en qué paso está su solicitud.
     */
    public void notifyInstanceAdvanced(ProcessInstance instance) {
        InstanceStatusUpdate event = InstanceStatusUpdate.builder()
                .type(InstanceStatusUpdate.TYPE_INSTANCE_ADVANCED)
                .instanceId(instance.getId())
                .currentNodeLabel(instance.getCurrentNodeLabel())
                .status(instance.getStatus())
                .progressPct(calcularProgreso(instance))
                .timestamp(Instant.now())
                .build();

        messaging.convertAndSendToUser(instance.getClientId(), "/queue/instance-status", event);
        log.debug("WS→ cliente '{}': trámite en '{}'", instance.getClientId(), instance.getCurrentNodeLabel());
    }

    /**
     * Proceso completado — broadcast a todos los admins.
     * Canal: /topic/admin/completed
     */
    public void notifyInstanceCompleted(ProcessInstance instance) {
        InstanceStatusUpdate event = InstanceStatusUpdate.builder()
                .type(InstanceStatusUpdate.TYPE_INSTANCE_COMPLETED)
                .instanceId(instance.getId())
                .currentNodeLabel("Completado")
                .status("COMPLETED")
                .progressPct(100)
                .timestamp(Instant.now())
                .build();

        messaging.convertAndSend("/topic/admin/completed", event);
        log.info("WS→ admin broadcast: instancia '{}' completada", instance.getId());
    }

    /**
     * Instancia rechazada — notificar al cliente
     */
    public void notifyInstanceRejected(ProcessInstance instance, String reason) {
        InstanceStatusUpdate event = InstanceStatusUpdate.builder()
                .type(InstanceStatusUpdate.TYPE_INSTANCE_REJECTED)
                .instanceId(instance.getId())
                .currentNodeLabel("Rechazado")
                .status("REJECTED")
                .progressPct(0)
                .timestamp(Instant.now())
                .build();

        messaging.convertAndSendToUser(instance.getClientId(), "/queue/instance-status", event);
        messaging.convertAndSend("/topic/admin/rejected", event);
        log.info("WS→ instancia '{}' rechazada: {}", instance.getId(), reason);
    }

    /**
     * Alerta de cuello de botella — broadcast al admin.
     * Canal: /topic/admin/bottlenecks
     */
    public void notifyBottleneck(TaskInstance task) {
        long overdueMinutes = ChronoUnit.MINUTES.between(task.getDueAt(), Instant.now());

        BottleneckAlert alert = BottleneckAlert.builder()
                .type(BottleneckAlert.TYPE_BOTTLENECK_DETECTED)
                .taskId(task.getId())
                .instanceId(task.getInstanceId())
                .nodeLabel(task.getNodeLabel())
                .assigneeId(task.getAssigneeId())
                .overdueMinutes(overdueMinutes)
                .timestamp(Instant.now())
                .build();

        messaging.convertAndSend("/topic/admin/bottlenecks", alert);
        log.warn("WS→ BOTTLENECK: tarea '{}' lleva {} min vencida", task.getNodeLabel(), overdueMinutes);
    }

    private int calcularProgreso(ProcessInstance instance) {
        if ("COMPLETED".equals(instance.getStatus())) return 100;
        if ("REJECTED".equals(instance.getStatus())) return 0;
        
        long completados = instance.getAuditLog().stream()
                .filter(a -> "NODE_COMPLETED".equals(a.getAction()))
                .count();
        return (int) Math.min(90, completados * 20);
    }
}