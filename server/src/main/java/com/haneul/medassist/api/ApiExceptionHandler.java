package com.haneul.medassist.api;

import com.haneul.medassist.api.ApiModels.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> notFound(NoSuchElementException e) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), false, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.", false, fields);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException e) {
        return response(HttpStatus.CONFLICT, "VERSION_CONFLICT", e.getMessage(), true, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), false, Map.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                               boolean retryable, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(code, message, fields, UUID.randomUUID().toString(), retryable));
    }
}

