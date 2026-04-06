package com.ai.fabric.platform.backend.vectorization.model;

import java.time.Instant;

public record VectorizationRunnerTokenSummary(
    String registrationId,
    String runnerMode,
    String registrationToken,
    String tokenHint,
    Instant expiresAt
) {
}
