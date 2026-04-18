package com.ai.fabric.product.shopify.bridge.store.model;

import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;

import java.time.Instant;

public record ShopifyBridgeMerchantSessionResponse(
    String shopDomain,
    String destination,
    String userId,
    Instant expiresAt,
    ShopifyInstallRecordSummary installRecord,
    ShopifyBridgeStoreSummary store
) {
}
