package com.ai.fabric.product.shopify.bridge.storefront.model;

public record ShopifyStorefrontEngagementEventRequest(
    String eventType,
    String pageType
) {
}
