package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CustomerSelfServiceExceptionHandler {
    @ExceptionHandler(CustomerSelfServiceException.class)
    public ResponseEntity<Map<String, Object>> handle(CustomerSelfServiceException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("timestamp", Instant.now().toString(),
            "success", false, "errorCode", exception.code().name(), "message", exception.code().name()));
    }
}
