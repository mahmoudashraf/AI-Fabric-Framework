package com.ai.fabric.realapps.chat.web;

import com.ai.infrastructure.chat.exception.ChatSessionAccessDeniedException;
import com.ai.infrastructure.chat.exception.ChatSessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ChatControllerAdvice {

    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ChatSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "error", "NOT_FOUND",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(ChatSessionAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(ChatSessionAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "error", "FORBIDDEN",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error", "BAD_REQUEST",
            "message", ex.getMessage()
        ));
    }
}

