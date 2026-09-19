package com.ai.fabric.runtime.smartbrain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record SmartBrainOperationView(
    String operationId,
    String triggerCode,
    String cloudEventId,
    String cloudEventType,
    String status,
    String operationUrl,
    JsonNode result,
    Failure failure,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    Instant expiresAt,
    boolean replayed
) {
    public record Failure(String code, String message) {
    }
}
