package com.ai.fabric.platform.backend.shopify.model;

public record UpdateShopifyStoreSourceSettingsRequest(
    Boolean productsEnabled,
    Boolean collectionsEnabled,
    Boolean pagesEnabled,
    Boolean policiesEnabled,
    Boolean articlesEnabled,
    Boolean metaobjectsEnabled
) {
}
