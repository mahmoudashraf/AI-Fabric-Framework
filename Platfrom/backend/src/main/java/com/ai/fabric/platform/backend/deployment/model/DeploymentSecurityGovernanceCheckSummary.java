package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentSecurityGovernanceCheckSummary(
    String key,
    String label,
    String status,
    String valueSummary,
    String message,
    String guidance
) {
}
