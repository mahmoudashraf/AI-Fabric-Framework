package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreCredentialSummary(
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
