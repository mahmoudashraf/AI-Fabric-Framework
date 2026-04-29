package com.ai.fabric.platform.backend.partner.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.ai.fabric.platform.backend.partner")
public class PartnerSecurityExceptionHandler {

    @ExceptionHandler(PartnerUnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(PartnerUnauthorizedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body("UNAUTHORIZED", exception.getMessage()));
    }

    @ExceptionHandler(PartnerForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(PartnerForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("BAD_REQUEST", exception.getMessage()));
    }

    private Map<String, Object> body(String code, String message) {
        return Map.of("success", false, "errorCode", code, "message", message);
    }
}
