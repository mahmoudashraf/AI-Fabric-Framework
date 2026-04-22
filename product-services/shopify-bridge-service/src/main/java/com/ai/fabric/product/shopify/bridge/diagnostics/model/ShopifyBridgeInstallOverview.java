package com.ai.fabric.product.shopify.bridge.diagnostics.model;

import java.time.Instant;

public record ShopifyBridgeInstallOverview(
    int totalCount,
    int installedCount,
    int uninstalledCount,
    int credentialReadyCount,
    Instant lastAuthenticatedAt,
    Instant lastUninstalledAt
) {
}
