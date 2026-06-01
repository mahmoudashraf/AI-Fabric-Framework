package com.ai.fabric.product.shopify.bridge.client.platform.model;

public record PlatformPublicConsumerRuntimeAssignmentResponse(
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
    PlatformPublicRuntimeEndpointsSummary endpoints,
    String guidance
) {
}
