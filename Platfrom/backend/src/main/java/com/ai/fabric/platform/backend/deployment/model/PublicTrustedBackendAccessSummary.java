package com.ai.fabric.platform.backend.deployment.model;

public record PublicTrustedBackendAccessSummary(
    boolean callerAuthConfigured,
    String authorizationHeader,
    boolean assertionValidationConfigured,
    String assertionAuthorizationHeader,
    String assertionTokenScheme,
    boolean acceptedIssuerPolicyConfigured,
    boolean acceptedAudiencePolicyConfigured,
    boolean platformDefaultIssuerPolicy,
    boolean externalIntegrationReady
) {
}
