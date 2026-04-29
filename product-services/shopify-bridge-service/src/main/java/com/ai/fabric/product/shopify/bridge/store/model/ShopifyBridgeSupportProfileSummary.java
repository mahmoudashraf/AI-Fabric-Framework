package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeSupportProfileSummary(
    String contactEmail,
    String contactUrl,
    String helpCenterUrl,
    String orderLookupPageUrl,
    String supportPolicyNote,
    boolean merchantHandoffConfigured
) {
}
