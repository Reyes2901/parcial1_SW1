package com.workflow.bpm.form;

import com.workflow.bpm.shared.exception.FormValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.file.storage:local}")
    private String storageType;

    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp",
            "application/pdf", "image/svg+xml"
    );

    /**
     * Sube un archivo y devuelve la URL de acceso.
     * En dev: guarda en disco local.
     * En prod: subir a S3 (implementar con AWS SDK).
     */
    public String upload(MultipartFile file, String folder) {
        validateFile(file);

        String filename = folder + "/" +
                UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());

        if ("local".equals(storageType)) {
            return saveLocal(file, filename);
        } else {
            return saveToS3(file, filename);
        }
    }

    private String saveLocal(MultipartFile file, String filename) {
        try {
            Path path = Paths.get(uploadDir, filename);
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());
            log.info("📁 Archivo guardado: {}", path);
            return "/api/files/" + filename;
        } catch (IOException e) {
            log.error("Error al guardar archivo: {}", e.getMessage());
            throw new RuntimeException("Error al guardar archivo: " + e.getMessage());
        }
    }

    private String saveToS3(MultipartFile file, String filename) {
        // TODO Día 7: implementar con AWS SDK S3Client
        log.warn("S3 no configurado en dev - guardando localmente como fallback");
        return saveLocal(file, filename);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FormValidationException(List.of("El archivo está vacío"));
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new FormValidationException(
                    List.of("El archivo supera el límite de 10 MB"));
        }
        if (file.getContentType() != null && !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new FormValidationException(
                    List.of("Tipo de archivo no permitido: " + file.getContentType()));
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Elimina un archivo del almacenamiento local
     */
    public boolean deleteLocal(String fileUrl) {
        try {
            String filename = fileUrl.replace("/api/files/", "");
            Path path = Paths.get(uploadDir, filename);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Error al eliminar archivo: {}", e.getMessage());
            return false;
        }
    }
}