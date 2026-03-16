package com.agent.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAccess(ResourceAccessException ex) {
        log.error("Resource access error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, 
                "Backend service is not reachable: " + ex.getMessage());
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, Object>> handleHttpClientError(HttpClientErrorException ex) {
        log.error("HTTP client error: {} - {}", ex.getStatusCode(), ex.getMessage());
        return buildErrorResponse(ex.getStatusCode(), ex.getMessage());
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<Map<String, Object>> handleHttpServerError(HttpServerErrorException ex) {
        log.error("HTTP server error: {} - {}", ex.getStatusCode(), ex.getMessage());
        return buildErrorResponse(ex.getStatusCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "An unexpected error occurred: " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", message);
        return new ResponseEntity<>(error, status);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            org.springframework.http.HttpStatusCode status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", status.value());
        error.put("message", message);
        return new ResponseEntity<>(error, status);
    }
}
