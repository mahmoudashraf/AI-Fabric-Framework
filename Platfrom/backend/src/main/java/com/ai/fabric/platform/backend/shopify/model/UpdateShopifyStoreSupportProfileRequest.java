package com.ai.fabric.platform.backend.shopify.model;

public record UpdateShopifyStoreSupportProfileRequest(
    String contactEmail,
    String contactUrl,
    String helpCenterUrl,
    String orderLookupPageUrl,
    String supportPolicyNote
) {
}
