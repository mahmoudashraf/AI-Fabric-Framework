package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentHostedVerificationDispatchSummary(
    String deploymentId,
    String releaseId,
    String deploymentVersionId,
    String profile,
    boolean verifyWrite,
    String summaryMessage,
    DeploymentHostedVerificationRunSummary run
) {
}
