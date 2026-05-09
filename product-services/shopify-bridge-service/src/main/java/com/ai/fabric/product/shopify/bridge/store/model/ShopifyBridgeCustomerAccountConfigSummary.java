package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeCustomerAccountConfigSummary(
    String shopDomain,
    String storefrontDomain,
    boolean storefrontDomainConfigured,
    String effectiveStorefrontDomain,
    String source,
    Instant updatedAt
) {
}
