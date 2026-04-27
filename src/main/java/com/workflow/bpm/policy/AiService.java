package com.workflow.bpm.policy;

import com.workflow.bpm.workflow.engine.WorkflowException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiService {

    private final WebClient webClient;

    public AiService(@Value("${app.ai-service.url:http://localhost:8000}") String aiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(aiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("🤖 AI Service configurado en: {}", aiUrl);
    }

    /**
     * Llama a FastAPI con el prompt del admin.
     * Devuelve un ProcessDefinition listo para guardar en MongoDB.
     */
    public ProcessDefinition generateDiagram(String prompt,
                                              String language,
                                              List<String> existingLanes) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("language", language != null ? language : "es");
        requestBody.put("include_forms", true);
        requestBody.put("max_nodes", 12);
        if (existingLanes != null && !existingLanes.isEmpty()) {
            requestBody.put("existing_lanes", existingLanes);
        }

        String shortPrompt = prompt.length() > 60 ? prompt.substring(0, 60) + "..." : prompt;
        log.info("🤖 Llamando FastAPI con prompt: {}", shortPrompt);

        try {
            ProcessDefinition def = webClient
                    .post()
                    .uri("/ai/generate-diagram")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("FastAPI 4xx: " + body)))
                    .onStatus(status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("FastAPI 5xx: " + body)))
                    .bodyToMono(ProcessDefinition.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (def != null) {
                log.info("✅ Diagrama generado por IA: {} nodos, {} transiciones",
                        def.getNodes() != null ? def.getNodes().size() : 0,
                        def.getTransitions() != null ? def.getTransitions().size() : 0);
            }
            return def;
        } catch (Exception e) {
            log.error("Error llamando a FastAPI: {}", e.getMessage());
            throw new WorkflowException("Error al generar diagrama con IA: " + e.getMessage());
        }
    }
}