package com.ai.fabric.platform.backend.vectorization.model;

import java.time.Instant;

public record VectorizationRunnerSessionSummary(
    String sessionToken,
    String registrationId,
    String deploymentId,
    String runnerMode,
    String compatibilityStatus,
    Instant expiresAt
) {
}
