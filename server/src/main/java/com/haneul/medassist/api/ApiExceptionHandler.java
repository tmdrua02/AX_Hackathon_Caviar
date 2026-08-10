package com.haneul.medassist.api;

import com.haneul.medassist.api.ApiModels.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import com.haneul.medassist.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE",
                "업로드 파일이 서버의 허용 크기를 초과했습니다.", false, Map.of());
    }

    @ExceptionHandler(ObjectStorage.StorageException.class)
    ResponseEntity<ApiError> storage(ObjectStorage.StorageException e) {
        boolean full = "STORAGE_FULL".equals(e.code());
        return response(full ? HttpStatus.INSUFFICIENT_STORAGE : HttpStatus.SERVICE_UNAVAILABLE,
                e.code(), full ? "서버 저장 공간이 부족합니다." : "파일 저장소를 사용할 수 없습니다.",
                !full, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled API error. traceId={}, method={}, path={}",
                traceId, request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "서버 처리 중 오류가 발생했습니다.",
                        Map.of(), traceId, true));
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                               boolean retryable, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(code, message, fields, UUID.randomUUID().toString(), retryable));
    }
}
