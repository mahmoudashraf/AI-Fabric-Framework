package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentSecretLiteralRiskSummary(
    String service,
    String path,
    String message
) {
}
