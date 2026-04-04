package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record VectorizationRunSummary(
    String id,
    String reason,
    String requestedStatus,
    String status,
    String runnerMode,
    List<String> entityScope,
    JsonNode progressSummary,
    JsonNode checkpointSummary,
    JsonNode errorSummary,
    String claimedByRegistrationId,
    String claimedBySessionId,
    String runnerInstanceId,
    String productVersion,
    String compatibilityVersion,
    Instant leaseExpiresAt,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    Instant updatedAt
) {
}
