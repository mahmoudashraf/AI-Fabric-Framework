package com.ai.fabric.platform.backend.deployment.model;

public record PublicConsumerDeploymentCredentialsResponse(
    String consumerId,
    String deploymentId,
    String runtimeBaseUrl,
    PublicDeploymentAccessSummary access,
    PublicDeploymentIntegrationSummary integration
) {
}
