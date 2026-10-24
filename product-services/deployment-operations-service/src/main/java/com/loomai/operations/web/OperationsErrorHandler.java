package com.loomai.operations.web;

import com.loomai.operations.service.OperationsException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OperationsErrorHandler {

    public record ApiError(String code, String message) {}

    @ExceptionHandler(OperationsException.class)
    ResponseEntity<ApiError> operations(OperationsException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(new ApiError("REQUEST_INVALID", "Request JSON or fields are invalid."));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ApiError> storageUnavailable(Exception ignored) {
        return ResponseEntity.status(503).body(new ApiError("OPERATIONS_STORAGE_UNAVAILABLE", "Operations storage is unavailable."));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> recordUnavailable(Exception ignored) {
        return ResponseEntity.status(503).body(new ApiError("OPERATIONS_RECORD_UNAVAILABLE", "Operations records could not be processed."));
    }
}
