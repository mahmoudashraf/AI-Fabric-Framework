package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VectorizationRunnerDiscoveryReportRequest(
    @NotBlank String sessionToken,
    @NotBlank String connectionId,
    @NotNull JsonNode discoverySummary
) {
}
