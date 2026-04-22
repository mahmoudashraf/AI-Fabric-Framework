package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeUpsertStoreRequest(
    String shopDomain,
    String displayName,
    String productServiceRef,
    String customerId,
    String deploymentId,
    String consumerId,
    String installStatus,
    String syncStatus,
    String sourceReadinessStatus,
    String widgetStatus,
    String onboardingStatus,
    Boolean productsEnabled,
    Boolean collectionsEnabled,
    Boolean pagesEnabled,
    Boolean policiesEnabled
) {
}
