package com.ai.fabric.product.shopify.bridge.customeraccount.model;

import java.time.Instant;

public record ShopifyCustomerAccountAuthStatus(
    boolean configured,
    boolean authenticated,
    String shopDomain,
    String shopperSessionRef,
    String scopes,
    Instant accessTokenExpiresAt,
    Instant sessionExpiresAt,
    String authStartUrl,
    String message
) {
}
