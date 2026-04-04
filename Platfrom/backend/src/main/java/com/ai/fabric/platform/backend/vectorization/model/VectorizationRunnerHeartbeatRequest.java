package com.ai.fabric.platform.backend.vectorization.model;

import jakarta.validation.constraints.NotBlank;

public record VectorizationRunnerHeartbeatRequest(
    @NotBlank String sessionToken,
    String runId
) {
}
