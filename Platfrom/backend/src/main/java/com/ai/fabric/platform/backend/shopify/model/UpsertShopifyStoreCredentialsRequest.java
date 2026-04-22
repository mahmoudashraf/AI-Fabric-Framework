package com.ai.fabric.platform.backend.shopify.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpsertShopifyStoreCredentialsRequest(
    @NotBlank @Size(max = 4096) String accessToken,
    @Size(max = 4096) String refreshToken,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt,
    @Size(max = 4000) String scopesText,
    Boolean expiring
) {
}
