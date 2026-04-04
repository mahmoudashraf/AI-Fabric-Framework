package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record VectorizationRunnerCheckpointRequest(
    @NotBlank String sessionToken,
    @NotBlank String runId,
    String entityType,
    @NotBlank String checkpointType,
    String checkpointValue,
    JsonNode progress,
    JsonNode details
) {
}
