package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentVerificationSnapshotSummary(
    String verificationRunId,
    String status,
    String summaryMessage,
    int passedChecks,
    int warningChecks,
    int failedChecks,
    int skippedChecks,
    Instant completedAt
) {
}
