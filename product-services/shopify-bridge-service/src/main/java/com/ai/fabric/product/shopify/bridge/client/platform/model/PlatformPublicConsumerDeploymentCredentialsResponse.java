package com.ai.fabric.product.shopify.bridge.client.platform.model;

public record PlatformPublicConsumerDeploymentCredentialsResponse(
    String consumerId,
    String deploymentId,
    String runtimeBaseUrl,
    PlatformPublicDeploymentIntegrationSummary integration
) {
}
