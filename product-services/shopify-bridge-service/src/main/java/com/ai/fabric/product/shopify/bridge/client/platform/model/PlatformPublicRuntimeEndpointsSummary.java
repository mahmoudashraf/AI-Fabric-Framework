package com.ai.fabric.product.shopify.bridge.client.platform.model;

public record PlatformPublicRuntimeEndpointsSummary(
    String chatBaseUrl,
    String chatQueryUrl,
    String suggestionsUrl,
    String conversationsUrl,
    String authContextUrl
) {
}
