package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record VectorizationVerificationStepSummary(
    String id,
    String stepKey,
    String stepName,
    String status,
    JsonNode details,
    Instant createdAt,
    Instant updatedAt
) {
}
