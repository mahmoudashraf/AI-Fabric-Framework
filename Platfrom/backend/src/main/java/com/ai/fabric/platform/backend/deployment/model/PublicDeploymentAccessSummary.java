package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentAccessSummary(
    String runtimeExposure,
    String connectorExposure,
    String runtimeAuthMode,
    String recommendedChatBaseUrl,
    String recommendedCrudBaseUrl,
    String preferredChatQueryUrl,
    String preferredSuggestionsUrl,
    String preferredConversationsUrl,
    String preferredConversationItemUrlTemplate,
    String preferredOperationalBaseUrl,
    String preferredAuthContextUrl,
    String preferredAuthOverviewUrl,
    boolean verifiedAuthContextRequired,
    boolean hostBackedRuntimeRequired,
    boolean directConnectorAccessSupported,
    boolean trustedBackendCallerAuthConfigured,
    String trustedBackendAuthorizationHeader,
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
