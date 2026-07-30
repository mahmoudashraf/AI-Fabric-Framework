package com.ai.fabric.platform.backend.deployment.model;

public record PublicConsumerRuntimeAssignmentResponse(
    String consumerId,
    String deploymentId,
    String runtimeBaseUrl,
    String runtimeAuthMode,
    String preferredIntegrationMode,
    String privateRuntimeIssuer,
    String privateRuntimeAudience,
    String privateRuntimeAudienceMode,
    String trustedBackendApiKeyHeader,
    String privateAssertionAuthorizationHeader,
    String privateAssertionTokenScheme,
    boolean externalIntegrationReady,
    String assignmentRevision,
    int cacheTtlSeconds,
    PublicRuntimeEndpointsSummary endpoints,
    String guidance
) {
}
