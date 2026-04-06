package com.ai.fabric.platform.backend.vectorization.model;

import java.time.Instant;

public record VectorizationRunnerSummary(
    String registrationId,
    String runnerMode,
    String registrationStatus,
    String compatibilityStatus,
    String tokenHint,
    Instant tokenExpiresAt,
    String runnerInstanceId,
    String productVersion,
    String compatibilityVersion,
    Instant lastConnectedAt,
    Instant lastSessionHeartbeatAt,
    Instant lastSessionExpiresAt
) {
}
