package com.ai.fabric.product.shopify.bridge.auth;

import java.time.Instant;

public record ShopifyMerchantSession(
    String shopDomain,
    String destination,
    String userId,
    Instant expiresAt
) {
}
