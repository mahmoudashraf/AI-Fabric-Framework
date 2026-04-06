package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record VectorizationSourceConnectionSummary(
    String id,
    String deploymentId,
    String name,
    String adapterType,
    String authMode,
    String status,
    JsonNode connectionConfig,
    JsonNode secretReferences,
    JsonNode discoverySummary,
    Instant createdAt,
    Instant updatedAt
) {
}
