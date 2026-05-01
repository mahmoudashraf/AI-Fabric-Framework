package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyStoreSupportProfileSummary(
    String contactEmail,
    String contactUrl,
    String helpCenterUrl,
    String orderLookupPageUrl,
    String supportPolicyNote,
    boolean merchantHandoffConfigured
) {
}
