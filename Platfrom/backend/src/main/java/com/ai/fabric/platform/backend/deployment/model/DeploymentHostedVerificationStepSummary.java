package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentHostedVerificationStepSummary(
    String status,
    String section,
    String message,
    String rawLine
) {
}
