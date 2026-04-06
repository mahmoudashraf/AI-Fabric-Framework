package com.ai.fabric.platform.backend.vectorization.model;

import java.time.Instant;

public record VectorizationRunnerHeartbeatSummary(
    String sessionStatus,
    String requestedStatus,
    Instant sessionExpiresAt,
    Instant leaseExpiresAt
) {
}
