package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreCredentialSummary(
    String status,
    boolean accessTokenPresent,
    boolean refreshTokenPresent,
    String accessTokenSecretRef,
    String refreshTokenSecretRef,
    Instant checkedAt,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt,
    String scopesText,
    boolean expiring
) {
}
