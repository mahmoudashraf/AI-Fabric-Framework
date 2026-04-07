package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentIntegrationSummary(
    String preferredChatBaseUrl,
    String preferredCrudBaseUrl,
    String publicRuntimeBootstrapUrl,
    String publicRuntimeAuthorizationHeader,
    String publicRuntimeTokenScheme,
    String runtimeAuthMode,
    boolean hostBackedRuntimeRequired,
    boolean connectorInternalOnly,
    String guidance
) {
}
