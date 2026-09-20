package com.ai.fabric.runtime.smartbrain;

import java.time.Instant;

public record SmartBrainOperationView(
    String operationId,
    String triggerCode,
    String cloudEventId,
    String cloudEventType,
    String status,
    String operationUrl,
    Object result,
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
