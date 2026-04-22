package com.workflow.bpm.analytics;

import com.workflow.bpm.notification.NotificationService;
import com.workflow.bpm.task.document.TaskInstance;
import com.workflow.bpm.task.document.TaskInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BottleneckDetector {

    private final TaskInstanceRepository taskRepo;
    private final NotificationService notifier;

    @Scheduled(fixedRate = 300_000)  // cada 5 minutos
    public void detectarCuellos() {
        List<TaskInstance> vencidas = taskRepo
                .findByStatusInAndDueAtBefore(
                        List.of(TaskInstance.STATUS_PENDING, TaskInstance.STATUS_IN_PROGRESS),
                        Instant.now());

        if (vencidas.isEmpty()) {
            log.debug("BottleneckDetector: 0 tareas vencidas");
            return;
        }

        log.warn("BottleneckDetector: {} tareas vencidas detectadas", vencidas.size());

        vencidas.forEach(task -> {
            // Escalar prioridad si aún no es URGENT
            if (!TaskInstance.PRIORITY_URGENT.equals(task.getPriority())) {
                task.setPriority(TaskInstance.PRIORITY_URGENT);
                taskRepo.save(task);
            }
            // Notificar al admin por WebSocket
            notifier.notifyBottleneck(task);
        });
    }
}