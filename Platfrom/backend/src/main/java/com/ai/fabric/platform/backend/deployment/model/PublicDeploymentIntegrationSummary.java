package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentIntegrationSummary(
    String preferredIntegrationMode,
    String preferredChatBaseUrl,
    String preferredCrudBaseUrl,
    String preferredOperationalBaseUrl,
    String trustedBackendAuthorizationHeader,
    String publicRuntimeBootstrapUrl,
    String publicRuntimeAuthorizationHeader,
    String publicRuntimeTokenScheme,
    String publicRuntimeTokenIssuerHint,
    String publicRuntimeDefaultAudience,
    String runtimeAuthMode,
    boolean hostBackedRuntimeRequired,
    boolean connectorInternalOnly,
    boolean trustedBackendCallerAuthConfigured,
    boolean publicRuntimeTokenValidationConfigured,
    boolean anonymousBootstrapSupported,
    boolean publicRuntimeAcceptedIssuerPolicyConfigured,
    boolean publicRuntimeAcceptedAudiencePolicyConfigured,
    boolean browserDirectRuntimeAccessSupported,
    String browserDirectChatBaseUrl,
    String browserDirectCrudBaseUrl,
    String backendMediatedRuntimeBaseUrl,
    String guidance
) {
}
