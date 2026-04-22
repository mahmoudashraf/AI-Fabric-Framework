package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record RecordShopifyStoreSourcePreflightRequest(
    List<ShopifyStoreSourcePreflightCategorySummary> categories
) {
}
