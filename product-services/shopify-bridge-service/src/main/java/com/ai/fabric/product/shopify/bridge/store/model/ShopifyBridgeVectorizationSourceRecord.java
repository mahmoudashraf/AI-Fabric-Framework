package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgeVectorizationSourceRecord(
    String id,
    String updatedAt,
    String title,
    String content,
    String sourceCategory,
    String documentType,
    String storefrontUrl,
    String handle,
    String vendor,
    String productType,
    String policyType
) {
}
