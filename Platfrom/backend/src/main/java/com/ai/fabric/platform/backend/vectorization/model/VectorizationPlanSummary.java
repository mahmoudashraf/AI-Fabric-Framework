package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record VectorizationPlanSummary(
    String id,
    String deploymentId,
    String name,
    String status,
    String runnerMode,
    String syncState,
    List<String> syncReasonCodes,
    JsonNode syncReasonDetails,
    String activeIndexedOutputHash,
    String lastSuccessfulIndexedOutputHash,
    String activeRevisionId,
    String sourceConnectionId,
    String lastRunId,
    String lastSuccessfulRunId,
    Instant manuallyConfirmedAt,
    Instant deferredReindexAt,
    VectorizationPlanRevisionSummary activeRevision,
    Instant createdAt,
    Instant updatedAt
) {
}
