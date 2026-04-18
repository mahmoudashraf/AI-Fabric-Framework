package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreLinkedCustomerSummary(
    String id,
    String name,
    String slug,
    String status,
    boolean platformManaged,
    Instant createdAt,
    Instant updatedAt
) {
}
