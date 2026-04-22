package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;
import java.util.List;

public record ShopifyStoreSourcePreflightSummary(
    String overallStatus,
    Instant checkedAt,
    List<ShopifyStoreSourcePreflightCategorySummary> categories
) {
}
