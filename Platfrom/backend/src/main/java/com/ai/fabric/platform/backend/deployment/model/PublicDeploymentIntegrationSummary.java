package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentIntegrationSummary(
    String preferredIntegrationMode,
    String preferredChatBaseUrl,
    String preferredCrudBaseUrl,
    String publicRuntimeBootstrapUrl,
    String publicRuntimeAuthorizationHeader,
    String publicRuntimeTokenScheme,
    String publicRuntimeTokenIssuerHint,
    String publicRuntimeDefaultAudience,
    String runtimeAuthMode,
    boolean hostBackedRuntimeRequired,
    boolean connectorInternalOnly,
    boolean publicRuntimeTokenValidationConfigured,
    boolean anonymousBootstrapSupported,
    String guidance
) {
}
