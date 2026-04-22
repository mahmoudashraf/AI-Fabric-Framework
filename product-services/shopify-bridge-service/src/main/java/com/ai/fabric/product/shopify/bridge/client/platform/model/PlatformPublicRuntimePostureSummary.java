package com.ai.fabric.product.shopify.bridge.client.platform.model;

public record PlatformPublicRuntimePostureSummary(
    String runtimeAuthMode,
    boolean verifiedAuthContextRequired,
    boolean hostBackedRuntimeRequired,
    boolean directConnectorAccessSupported
) {
}
