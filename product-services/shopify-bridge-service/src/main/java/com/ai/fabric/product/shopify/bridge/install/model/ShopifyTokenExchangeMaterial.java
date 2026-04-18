package com.ai.fabric.product.shopify.bridge.install.model;

import java.time.Instant;

public record ShopifyTokenExchangeMaterial(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt,
    String scopesText,
    boolean expiring
) {
}
