package com.loomai.inbox.web;

import com.loomai.inbox.persistence.BasicInboxRepository.InboxConflictException;
import com.loomai.inbox.persistence.BasicInboxRepository.InboxNotFoundException;
import com.loomai.inbox.service.InboxSecurityService.InboxSecurityException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InboxErrorHandler {

    @ExceptionHandler(InboxSecurityException.class)
    public ResponseEntity<Map<String, Object>> security(InboxSecurityException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(InboxNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(InboxNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(InboxConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(InboxConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.code(), exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "success", false,
            "code", code,
            "message", message,
            "timestamp", Instant.now().toString()
        ));
    }
}
