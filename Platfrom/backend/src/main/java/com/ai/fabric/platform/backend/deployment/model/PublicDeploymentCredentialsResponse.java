package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentCredentialsResponse(
    String clientId,
    String externalDeploymentKey,
    String deploymentId,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    PublicDeploymentAccessSummary access
) {
}
