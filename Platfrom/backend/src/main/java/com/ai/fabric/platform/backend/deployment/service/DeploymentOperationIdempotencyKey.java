package com.ai.fabric.platform.backend.deployment.service;

import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

final class DeploymentOperationIdempotencyKey {
    private DeploymentOperationIdempotencyKey() { }

    static String require(String value) {
        if (value == null || value.isEmpty() || value.length() > 128
            || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new ResponseStatusException(BAD_REQUEST,
                "Idempotency-Key is required and must contain 1 to 128 visible ASCII characters.");
        }
        return value;
    }
}
