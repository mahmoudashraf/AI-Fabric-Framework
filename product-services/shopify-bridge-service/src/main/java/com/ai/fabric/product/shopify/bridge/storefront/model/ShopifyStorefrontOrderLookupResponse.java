package com.ai.fabric.product.shopify.bridge.storefront.model;

public record ShopifyStorefrontOrderLookupResponse(
    boolean available,
    boolean matched,
    String status,
    String message,
    String guidance,
    ShopifyStorefrontOrderLookupSummary order
) {
}
