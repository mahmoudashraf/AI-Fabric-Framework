package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertVectorizationSourceConnectionRequest(
    @NotBlank String name,
    @NotBlank String adapterType,
    @NotBlank String authMode,
    @NotNull JsonNode connectionConfig,
    JsonNode secretReferences,
    JsonNode discoverySummary
) {
}
