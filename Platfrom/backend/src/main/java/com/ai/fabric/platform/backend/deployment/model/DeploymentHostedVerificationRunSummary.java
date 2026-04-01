package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentHostedVerificationRunSummary(
    String id,
    String deploymentId,
    String releaseId,
    String deploymentVersionId,
    String verificationProfile,
    String runnerType,
    String scriptPath,
    String status,
    boolean verifyWrite,
    String summaryMessage,
    String logOutput,
    Integer exitCode,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt
) {
}
