package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentAccessSummary(
    String runtimeExposure,
    String connectorExposure,
    String runtimeAuthMode,
    String recommendedChatBaseUrl,
    String recommendedCrudBaseUrl,
    boolean hostBackedRuntimeRequired,
    boolean directConnectorAccessSupported,
    String guidance
) {
}
