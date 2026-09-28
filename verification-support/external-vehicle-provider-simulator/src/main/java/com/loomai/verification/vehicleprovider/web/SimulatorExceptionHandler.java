package com.loomai.verification.vehicleprovider.web;

import com.loomai.verification.vehicleprovider.service.ProviderApiException;
import com.loomai.verification.vehicleprovider.service.SimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class SimulatorExceptionHandler {

    private final SimulatorService simulator;

    public SimulatorExceptionHandler(SimulatorService simulator) {
        this.simulator = simulator;
    }

    @ExceptionHandler(ProviderApiException.class)
    public ResponseEntity<Map<String, Object>> provider(ProviderApiException ex) {
        return error(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> badRequest(Exception ex) {
        return error(400, "INVALID_REQUEST", safeMessage(ex));
    }

    private ResponseEntity<Map<String, Object>> error(int status, String code, String message) {
        String requestId = simulator.newRequestId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("requestId", requestId);
        return ResponseEntity.status(status)
            .header("X-Simulator-Request", requestId)
            .body(body);
    }

    private static String safeMessage(Exception ex) {
        if (ex instanceof IllegalArgumentException && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "The request does not satisfy the simulator contract.";
    }
}
