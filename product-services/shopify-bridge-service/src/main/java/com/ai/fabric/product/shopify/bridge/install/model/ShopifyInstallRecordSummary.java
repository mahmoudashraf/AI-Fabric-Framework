package com.ai.fabric.product.shopify.bridge.install.model;

import java.time.Instant;

public record ShopifyInstallRecordSummary(
    String shopDomain,
    String status,
    String shopUrl,
    String userId,
    String appBridgeHost,
    String accessTokenSecretRef,
    String refreshTokenSecretRef,
    String scopesText,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt,
    Instant installedAt,
    Instant lastAuthenticatedAt,
    Instant lastUninstalledAt
) {
}
