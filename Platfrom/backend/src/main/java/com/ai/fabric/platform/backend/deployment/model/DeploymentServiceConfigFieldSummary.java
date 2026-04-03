package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentServiceConfigFieldSummary(
    String key,
    String label,
    String valueSummary,
    boolean required,
    boolean configured,
    String source,
    String guidance
) {
}
