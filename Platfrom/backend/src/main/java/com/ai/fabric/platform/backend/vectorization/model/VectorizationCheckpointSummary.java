package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record VectorizationCheckpointSummary(
    String id,
    String entityType,
    String checkpointType,
    String checkpointValue,
    JsonNode progress,
    JsonNode details,
    Instant createdAt,
    Instant updatedAt
) {
}
