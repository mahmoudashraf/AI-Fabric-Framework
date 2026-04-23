package com.ai.fabric.product.shopify.bridge.storefront.model;

public record ShopifyStorefrontOrderLookupLineItemSummary(
    String title,
    String variantTitle,
    int quantity
) {
}
