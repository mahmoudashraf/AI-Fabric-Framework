package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentRemediationExecutionSummary(
    String actionKey,
    String status,
    String message,
    String referenceType,
    String referenceId
) {
}
