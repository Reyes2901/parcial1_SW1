package com.workflow.bpm.form;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService uploadService;

    /**
     * El frontend sube primero el archivo, recibe la URL,
     * y luego incluye esa URL en el formData al completar la tarea.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        String url = uploadService.upload(file, folder);
        return ResponseEntity.ok(Map.of(
                "url", url,
                "filename", file.getOriginalFilename()
        ));
    }

    /**
     * Subir múltiples archivos
     */
    @PostMapping("/upload/multiple")
    public ResponseEntity<Map<String, Object>> uploadMultiple(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        List<Map<String, String>> uploaded = java.util.Arrays.stream(files)
                .map(file -> Map.of(
                        "url", uploadService.upload(file, folder),
                        "filename", file.getOriginalFilename()
                ))
                .toList();

        return ResponseEntity.ok(Map.of("files", uploaded));
    }

    /**
     * Servir archivos locales en dev
     */
    @GetMapping("/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI().replace("/api/files/", "");
        Path filePath = Paths.get("./uploads").resolve(path).normalize();
        Resource resource = new UrlResource(filePath.toUri());
        
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Archivo no encontrado: " + path);
        }
        
        // Detectar tipo de contenido
        String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    /**
     * Eliminar archivo (solo admin)
     */
    @DeleteMapping("/**")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> delete(HttpServletRequest request) {
        String path = request.getRequestURI().replace("/api/files/", "");
        boolean deleted = uploadService.deleteLocal(path);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
    private final SignatureService signatureService;  // 👈 Agregar en constructor

    /**
     * Guardar firma digital enviada como base64
     */
    @PostMapping("/signature")
    public ResponseEntity<Map<String, String>> saveSignature(
            @RequestBody Map<String, String> body,
            @RequestParam String instanceId) {

        String url = signatureService.saveSignature(body.get("signature"), instanceId);
        return ResponseEntity.ok(Map.of("url", url));
    }
}