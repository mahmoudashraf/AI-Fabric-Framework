package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreWidgetSummary(
    String status,
    Instant checkedAt,
    String channel,
    String message
) {
}
