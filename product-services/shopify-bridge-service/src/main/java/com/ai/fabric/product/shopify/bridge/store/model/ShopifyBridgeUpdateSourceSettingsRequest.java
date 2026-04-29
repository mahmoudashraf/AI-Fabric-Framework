package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeUpdateSourceSettingsRequest(
    Boolean productsEnabled,
    Boolean collectionsEnabled,
    Boolean pagesEnabled,
    Boolean policiesEnabled,
    Boolean articlesEnabled,
    Boolean metaobjectsEnabled
) {
}
