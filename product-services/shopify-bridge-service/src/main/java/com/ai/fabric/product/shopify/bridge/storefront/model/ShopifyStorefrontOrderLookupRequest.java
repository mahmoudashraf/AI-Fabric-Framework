package com.ai.fabric.product.shopify.bridge.storefront.model;

public record ShopifyStorefrontOrderLookupRequest(
    String orderNumber,
    String email,
    String surfaceId
) {
}
