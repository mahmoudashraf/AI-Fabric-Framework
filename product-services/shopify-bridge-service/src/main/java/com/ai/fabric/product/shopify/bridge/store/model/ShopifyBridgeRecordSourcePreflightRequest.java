package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeRecordSourcePreflightRequest(
    List<ShopifyBridgeStoreSourcePreflightCategorySummary> categories
) {
}
