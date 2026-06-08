package com.workflow.bpm.document.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementación local (disco) de {@link FileStorageProvider}, reutilizando el
 * mismo directorio de uploads del mecanismo actual ({@code app.file.upload-dir}).
 * <p>
 * No modifica el comportamiento de subida/firma existente: sólo provee lectura
 * desacoplada para el repositorio documental.
 */
@Component
@Slf4j
public class LocalFileStorageProvider implements FileStorageProvider {

    private static final String FILE_URL_PREFIX = "/api/files/";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public boolean exists(String fileId) {
        Path path = resolve(fileId);
        return Files.exists(path) && Files.isReadable(path);
    }

    @Override
    public InputStream read(String fileId) throws IOException {
        return Files.newInputStream(resolve(fileId));
    }

    @Override
    public long size(String fileId) {
        try {
            return Files.size(resolve(fileId));
        } catch (IOException e) {
            return -1L;
        }
    }

    @Override
    public String contentType(String fileId) {
        try {
            String type = Files.probeContentType(resolve(fileId));
            return type != null ? type : DEFAULT_CONTENT_TYPE;
        } catch (IOException e) {
            return DEFAULT_CONTENT_TYPE;
        }
    }

    /**
     * Resuelve el path físico a partir del fileId (URL pública), evitando
     * traversal fuera del directorio de uploads.
     */
    private Path resolve(String fileId) {
        String relative = fileId == null ? "" : fileId;
        if (relative.startsWith(FILE_URL_PREFIX)) {
            relative = relative.substring(FILE_URL_PREFIX.length());
        }
        return Paths.get(uploadDir).resolve(relative).normalize();
    }
}
