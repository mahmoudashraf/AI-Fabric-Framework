package com.ai.fabric.platform.backend.web;

import com.ai.fabric.platform.backend.deployment.service.RailwayProvisioningConfigurationException;
import com.ai.fabric.platform.backend.deployment.service.RailwayProvisioningException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class PlatformApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolvedStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String errorCode = resolvedStatus.name();
        String message = ex.getReason() == null || ex.getReason().isBlank()
            ? resolvedStatus.getReasonPhrase()
            : ex.getReason();
        return ResponseEntity.status(resolvedStatus).body(errorBody(
            errorCode,
            message
        ));
    }

    @ExceptionHandler(RailwayProvisioningConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handleRailwayProvisioningConfiguration(
        RailwayProvisioningConfigurationException ex
    ) {
        return ResponseEntity.badRequest().body(errorBody(
            "PROVISIONING_CONFIG_INVALID",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(RailwayProvisioningException.class)
    public ResponseEntity<Map<String, Object>> handleRailwayProvisioningFailure(RailwayProvisioningException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody(
            "PROVISIONING_UPSTREAM_FAILURE",
            ex.getMessage()
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
            "DATA_INTEGRITY_VIOLATION",
            "The operation could not be completed because related platform records still exist."
        ));
    }

    private Map<String, Object> errorBody(String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("success", false);
        body.put("errorCode", errorCode);
        body.put("message", message);
        return body;
    }
}
