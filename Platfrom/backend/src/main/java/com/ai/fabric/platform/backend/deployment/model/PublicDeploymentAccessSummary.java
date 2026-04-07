package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentAccessSummary(
    String runtimeExposure,
    String connectorExposure,
    String runtimeAuthMode,
    String recommendedChatBaseUrl,
    String recommendedCrudBaseUrl,
    boolean hostBackedRuntimeRequired,
    boolean directConnectorAccessSupported,
    boolean publicRuntimeTokenValidationConfigured,
    boolean anonymousBootstrapSupported,
    String publicRuntimeBootstrapUrl,
    String publicRuntimeAuthorizationHeader,
    String publicRuntimeTokenScheme,
    boolean publicRuntimeAcceptedIssuerPolicyConfigured,
    boolean publicRuntimeAcceptedAudiencePolicyConfigured,
    String publicRuntimeTokenIssuerHint,
    String publicRuntimeDefaultAudience,
    String guidance
) {
}
