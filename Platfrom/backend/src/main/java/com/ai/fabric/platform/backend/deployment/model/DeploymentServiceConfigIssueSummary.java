package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentServiceConfigIssueSummary(
    String severity,
    String code,
    String path,
    String message
) {
}
