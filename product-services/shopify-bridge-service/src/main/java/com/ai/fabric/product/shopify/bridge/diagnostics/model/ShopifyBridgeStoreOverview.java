package com.ai.fabric.product.shopify.bridge.diagnostics.model;

import java.time.Instant;

public record ShopifyBridgeStoreOverview(
    String platformAccessStatus,
    String platformAccessMessage,
    int totalCount,
    int readyForGoLiveCount,
    int storefrontReadyCount,
    int liveCount,
    int blockedCount,
    Instant lastWebhookAt
) {
}
