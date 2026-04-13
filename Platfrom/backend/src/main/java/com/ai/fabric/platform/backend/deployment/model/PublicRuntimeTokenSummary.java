package com.ai.fabric.platform.backend.deployment.model;

public record PublicRuntimeTokenSummary(
    boolean tokenValidationConfigured,
    boolean anonymousBootstrapSupported,
    String bootstrapUrl,
    String authorizationHeader,
    String tokenScheme,
    boolean acceptedIssuerPolicyConfigured,
    boolean acceptedAudiencePolicyConfigured,
    String tokenIssuerHint,
    String defaultAudience
) {
}
