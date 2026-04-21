package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;
import java.util.List;

public record PlatformVerificationSuiteRunSummary(
    String id,
    String suiteKey,
    String suiteLabel,
    String status,
    boolean releaseBlocking,
    String summaryMessage,
    String requestedByActorId,
    String requestedByRole,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    List<PlatformVerificationSuiteStageRunSummary> stages
) {
}
