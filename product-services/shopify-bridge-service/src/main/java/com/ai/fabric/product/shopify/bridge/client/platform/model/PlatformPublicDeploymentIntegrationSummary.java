package com.ai.fabric.product.shopify.bridge.client.platform.model;

public record PlatformPublicDeploymentIntegrationSummary(
    String preferredIntegrationMode,
    PlatformPublicRuntimePostureSummary posture,
    PlatformPublicRuntimeEndpointsSummary runtime,
    String guidance
) {
}
