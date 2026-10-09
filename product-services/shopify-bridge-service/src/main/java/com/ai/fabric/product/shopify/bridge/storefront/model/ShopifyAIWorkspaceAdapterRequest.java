package com.ai.fabric.product.shopify.bridge.storefront.model;

public record ShopifyAIWorkspaceAdapterRequest(
    String schemaVersion,
    String installationId,
    String assignmentRevision
) {
}
