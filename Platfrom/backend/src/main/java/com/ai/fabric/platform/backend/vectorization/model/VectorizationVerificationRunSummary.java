package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record VectorizationVerificationRunSummary(
    String id,
    String verificationType,
    String executionMode,
    String status,
    List<String> entityScope,
    JsonNode summary,
    String linkedVectorizationRunId,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    Instant updatedAt
) {
}
