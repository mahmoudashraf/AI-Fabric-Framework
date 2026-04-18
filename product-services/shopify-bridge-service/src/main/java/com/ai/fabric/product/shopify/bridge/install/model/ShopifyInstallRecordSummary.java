package com.ai.fabric.product.shopify.bridge.install.model;

import java.time.Instant;

public record ShopifyInstallRecordSummary(
    String shopDomain,
    String status,
    String shopUrl,
    String userId,
    String appBridgeHost,
    Instant installedAt,
    Instant lastAuthenticatedAt,
    Instant lastUninstalledAt
) {
}
