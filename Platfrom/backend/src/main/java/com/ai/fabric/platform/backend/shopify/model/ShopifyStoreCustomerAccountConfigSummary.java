package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreCustomerAccountConfigSummary(
    String shopDomain,
    String storefrontDomain,
    boolean storefrontDomainConfigured,
    String effectiveStorefrontDomain,
    String source,
    Instant updatedAt
) {
}
