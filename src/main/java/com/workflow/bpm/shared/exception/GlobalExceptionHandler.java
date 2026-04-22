package com.workflow.bpm.shared.exception;

import com.workflow.bpm.workflow.engine.WorkflowException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidProcessException.class)
    public ResponseEntity<ErrorResponse> handleInvalidProcess(InvalidProcessException ex) {
        return ResponseEntity.status(422)
                .body(new ErrorResponse("INVALID_PROCESS", ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse("FORBIDDEN", "Sin permisos para esta operación"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(new ErrorResponse("VALIDATION_ERROR", msg));
    }

    //Nuevo manejador para WorkflowException
    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<ErrorResponse> handleWorkflow(WorkflowException ex) {
        return ResponseEntity.status(422)
                .body(new ErrorResponse("WORKFLOW_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(500)
                .body(new ErrorResponse("INTERNAL_ERROR", "Error interno del servidor"));
    }
    
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("FILE_TOO_LARGE", "El archivo excede el tamaño máximo permitido"));
    }
    @ExceptionHandler(FormValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFormValidation(FormValidationException ex) {
        return ResponseEntity.status(400).body(Map.of(
                "code", "FORM_VALIDATION_ERROR",
                "errors", ex.getErrors()
        ));
    }

  
}