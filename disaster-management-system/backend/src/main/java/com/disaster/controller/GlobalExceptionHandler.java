package com.disaster.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new java.util.HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("error", "Validation failed");
        response.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(com.disaster.exception.RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(com.disaster.exception.RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of(
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "error", "Too Many Requests",
                        "message", ex.getMessage(),
                        "retryAfterSeconds", ex.getRetryAfterSeconds()
                ));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Invalid credentials"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.disaster.exception.ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(com.disaster.exception.ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.disaster.exception.AccountNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleNotVerified(com.disaster.exception.AccountNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "Account not verified",
                        "message", ex.getMessage(),
                        "email", ex.getEmail()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        String correlationId = java.util.UUID.randomUUID().toString();
        logger.error("Unhandled exception [correlationId={}]: {}", correlationId, ex.getMessage(), ex);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal server error");
        body.put("message", "An unexpected error occurred. Reference ID: " + correlationId);
        body.put("correlationId", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
