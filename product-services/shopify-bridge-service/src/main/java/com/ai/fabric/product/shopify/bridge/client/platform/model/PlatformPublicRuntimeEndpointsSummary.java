package com.ai.fabric.product.shopify.bridge.client.platform.model;

public record PlatformPublicRuntimeEndpointsSummary(
    String chatBaseUrl,
    String crudBaseUrl,
    String chatQueryUrl,
    String queryOnceUrl,
    String suggestionsUrl,
    String conversationsUrl,
    String conversationItemUrlTemplate,
    String operationalBaseUrl,
    String healthUrl,
    String authContextUrl,
    String authOverviewUrl
) {
}
