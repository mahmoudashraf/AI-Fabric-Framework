package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record VectorizationPlanRevisionSummary(
    String id,
    int revisionNumber,
    String status,
    String sourceConnectionId,
    JsonNode entityScope,
    JsonNode mappingConfig,
    JsonNode executionConfig,
    String indexedOutputHash,
    Instant createdAt,
    Instant updatedAt
) {
}
