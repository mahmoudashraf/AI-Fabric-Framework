package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreDeploymentVersionSummary(
    String id,
    String versionLabel,
    String status,
    Instant publishedAt
) {
}
