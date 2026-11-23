package com.intervia.config;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Map<String, String> fields;

    public ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
        this.fields = null;
    }

    public ApiException(String code, HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.status = status;
        this.fields = fields;
    }
}
