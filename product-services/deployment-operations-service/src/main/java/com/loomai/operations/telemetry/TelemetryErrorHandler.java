package com.loomai.operations.telemetry;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TelemetryController.class)
public class TelemetryErrorHandler {
    public record ApiError(String code, String message) {}

    @ExceptionHandler(TelemetryException.class)
    ResponseEntity<ApiError> telemetry(TelemetryException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, IllegalStateException.class})
    ResponseEntity<ApiError> unavailable(Exception ignored) {
        return ResponseEntity.status(503).body(new ApiError("TELEMETRY_STORAGE_UNAVAILABLE", "Telemetry storage is unavailable."));
    }
}
