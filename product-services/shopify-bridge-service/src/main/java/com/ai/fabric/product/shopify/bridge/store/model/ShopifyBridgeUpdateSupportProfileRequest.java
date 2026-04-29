package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeUpdateSupportProfileRequest(
    String contactEmail,
    String contactUrl,
    String helpCenterUrl,
    String orderLookupPageUrl,
    String supportPolicyNote
) {
}
