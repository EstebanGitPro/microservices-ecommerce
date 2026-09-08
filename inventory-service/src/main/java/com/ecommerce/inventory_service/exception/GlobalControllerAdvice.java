package com.ecommerce.inventory_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    private static final String TRACE_ID = "traceId";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        String traceId = newTraceId();

        log.warn("[{}] Recurso no encontrado - Path: {} , Message: {}",
                traceId, request.getDescription(false), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Recurso no encontrado ");
        problemDetail.setType(URI.create("https://api.ecommerce.com/errors/not-found"));
        problemDetail.setProperty("Timestamp", Instant.now());
        problemDetail.setProperty(TRACE_ID, traceId);

        problemDetail.setProperty("Resource", ex.getResourceName());
        problemDetail.setProperty("Field", ex.getFieldName());
        problemDetail.setProperty("Value", ex.getFieldValue());

        return problemDetail;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(DuplicateResourceException ex, WebRequest request) {
        String traceId = newTraceId();

        log.warn("[{}] Recurso duplicado - Path: {} , Message: {}",
                traceId, request.getDescription(false), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Recurso duplicado");
        problemDetail.setType(URI.create("https://api.ecommerce.com/errors/duplicate"));
        problemDetail.setProperty("Timestamp", Instant.now());
        problemDetail.setProperty(TRACE_ID, traceId);

        problemDetail.setProperty("Resource", ex.getResourceName());
        problemDetail.setProperty("Field", ex.getFieldName());
        problemDetail.setProperty("Value", ex.getFieldValue());

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String traceId = newTraceId();

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La validacón fallo en uno o más campos");

        problemDetail.setTitle("Error de validacón");
        problemDetail.setType(URI.create("https://api.ecommerce.com/errors/error-validation"));
        problemDetail.setProperty("Timestamp", Instant.now());
        problemDetail.setProperty(TRACE_ID, traceId);

        Map<String, String> errorMap = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((fieldError) -> {

            errorMap.put(fieldError.getField(), fieldError.getDefaultMessage());

        });

        problemDetail.setProperty("errors", errorMap);

        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception ex, WebRequest request) {
        String traceId = newTraceId();

        log.error("[{}] Ha ocurrido un error inesperado: {} , Message: {}",
                traceId, request.getDescription(false), ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado, contactar con el administrador");

        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.ecommerce.com/errors/internal"));
        problemDetail.setProperty("Timestamp", Instant.now());
        problemDetail.setProperty(TRACE_ID, traceId);

        return problemDetail;
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}