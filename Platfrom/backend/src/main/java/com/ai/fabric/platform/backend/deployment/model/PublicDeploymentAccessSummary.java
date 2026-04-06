package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentAccessSummary(
    String runtimeExposure,
    String connectorExposure,
    String recommendedChatBaseUrl,
    String recommendedCrudBaseUrl,
    boolean directConnectorAccessSupported,
    String guidance
) {
}
