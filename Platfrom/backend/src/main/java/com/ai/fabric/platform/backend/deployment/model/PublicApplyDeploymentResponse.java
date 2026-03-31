package com.ai.fabric.platform.backend.deployment.model;

public record PublicApplyDeploymentResponse(
    String clientId,
    String externalDeploymentKey,
    String deploymentId,
    String versionId,
    String versionLabel,
    boolean idempotentReplay,
    DeploymentReleaseSummary release
) {
}
