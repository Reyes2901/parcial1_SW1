package com.workflow.bpm.form;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
public class SignatureService {

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Recibe una firma en base64 (data:image/png;base64,...),
     * la guarda como archivo PNG y devuelve la URL.
     */
    public String saveSignature(String base64Data, String instanceId) {
        // Extraer los bytes del base64
        String base64 = base64Data.contains(",")
                ? base64Data.split(",")[1]
                : base64Data;

        byte[] imageBytes = Base64.getDecoder().decode(base64);

        String filename = "signatures/" + instanceId + "_" +
                UUID.randomUUID() + ".png";

        try {
            Path path = Paths.get(uploadDir, filename);
            Files.createDirectories(path.getParent());
            Files.write(path, imageBytes);
            log.info("✍️ Firma guardada: {}", path);
            return "/api/files/" + filename;
        } catch (IOException e) {
            log.error("Error guardando firma: {}", e.getMessage());
            throw new RuntimeException("Error guardando firma: " + e.getMessage());
        }
    }
}