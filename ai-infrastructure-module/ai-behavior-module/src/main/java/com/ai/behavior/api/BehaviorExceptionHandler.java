package com.ai.behavior.api;

import com.ai.behavior.exception.BehaviorIngestionException;
import com.ai.behavior.exception.BehaviorModuleException;
import com.ai.behavior.exception.BehaviorValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class BehaviorExceptionHandler {

    @ExceptionHandler({BehaviorValidationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception ex) {
        return ResponseEntity.badRequest().body(errorPayload("validation_error", ex.getMessage()));
    }

    @ExceptionHandler(BehaviorIngestionException.class)
    public ResponseEntity<Map<String, Object>> handleIngestion(BehaviorIngestionException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorPayload("ingestion_error", ex.getMessage()));
    }

    @ExceptionHandler(BehaviorModuleException.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(BehaviorModuleException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorPayload("behavior_error", ex.getMessage()));
    }

    private Map<String, Object> errorPayload(String code, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("code", code);
        payload.put("message", message);
        return payload;
    }
}
