package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record VectorizationFailureBucketSummary(
    String id,
    String entityType,
    String errorCode,
    String summary,
    JsonNode sample,
    Integer occurrences,
    Instant createdAt,
    Instant updatedAt
) {
}
