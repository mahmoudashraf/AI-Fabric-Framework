package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeMerchantSessionResponse(
    String shopDomain,
    String destination,
    String userId,
    Instant expiresAt,
    ShopifyBridgeStoreSummary store
) {
}
