package com.ai.fabric.platform.backend.deployment.model;

import java.time.Duration;
import java.time.Instant;

public record PlatformVerificationReleaseGateSummary(
    String suiteKey,
    String suiteLabel,
    boolean ready,
    String status,
    String summaryMessage,
    Duration freshnessWindow,
    Instant evaluatedAt,
    Instant expiresAt,
    PlatformVerificationSuiteRunSummary latestRun
) {
}
