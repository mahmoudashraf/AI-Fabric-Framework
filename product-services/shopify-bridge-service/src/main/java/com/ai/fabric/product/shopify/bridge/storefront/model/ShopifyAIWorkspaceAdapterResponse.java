package com.ai.fabric.product.shopify.bridge.storefront.model;

import java.util.Map;

public record ShopifyAIWorkspaceAdapterResponse(
    String schemaVersion,
    String chatBaseUrl,
    Map<String, String> routes,
    Map<String, String> defaultHeaders,
    String fetchCredentials,
    boolean probeAuthContextOnOpen,
    boolean probeShellConfigOnOpen,
    Map<String, Boolean> features
) {
}
