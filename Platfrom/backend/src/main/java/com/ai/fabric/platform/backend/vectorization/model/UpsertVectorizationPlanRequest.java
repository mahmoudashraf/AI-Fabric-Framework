package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record UpsertVectorizationPlanRequest(
    @NotBlank String name,
    @NotBlank String runnerMode,
    String sourceConnectionId,
    JsonNode entityScope,
    JsonNode mappingConfig,
    JsonNode executionConfig
) {
}
