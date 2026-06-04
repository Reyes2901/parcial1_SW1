package com.workflow.bpm.shared.exception;

import com.workflow.bpm.workflow.engine.WorkflowException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(404)
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(InvalidProcessException.class)
    public ResponseEntity<ErrorResponse> handleInvalidProcess(InvalidProcessException ex, HttpServletRequest request) {
        return ResponseEntity.status(422).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(422)
                .error("Unprocessable Entity")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex, HttpServletRequest request) {
        return ResponseEntity.status(400).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Bad Request")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(400).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Bad Request")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(403)
                .error("Forbidden")
                .message("Sin permisos para esta operación")
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Bad Request")
                .message(msg)
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<ErrorResponse> handleWorkflow(WorkflowException ex, HttpServletRequest request) {
        return ResponseEntity.status(422).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(422)
                .error("Workflow Error")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(400).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Bad Request")
                .message("El archivo excede el tamaño máximo permitido")
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(FormValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFormValidation(FormValidationException ex) {
        return ResponseEntity.status(400).body(Map.of(
                "code", "FORM_VALIDATION_ERROR",
                "errors", ex.getErrors()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(500).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(500)
                .error("Internal Server Error")
                .message("Error interno del servidor")
                .path(request.getRequestURI())
                .build());
    }
}