package com.intervia.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", ex.getCode());
        error.put("message", ex.getMessage());
        if (ex.getFields() != null && !ex.getFields().isEmpty()) {
            error.put("fields", ex.getFields());
        }
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> fields.put(err.getField(), err.getDefaultMessage()));

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "validation_error");
        error.put("message", "Dữ liệu chưa hợp lệ.");
        error.put("fields", fields);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "internal_error");
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", error));
    }
}
