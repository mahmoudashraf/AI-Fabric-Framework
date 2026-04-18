package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreResolvedCredentialsSummary(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt,
    String scopesText,
    boolean expiring
) {
}
