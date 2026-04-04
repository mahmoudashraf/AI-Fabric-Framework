package com.ai.fabric.platform.backend.vectorization.model;

import jakarta.validation.constraints.NotBlank;

public record VectorizationRunnerSessionRequest(
    @NotBlank String registrationToken,
    @NotBlank String runnerInstanceId,
    String productVersion,
    String compatibilityVersion
) {
}
