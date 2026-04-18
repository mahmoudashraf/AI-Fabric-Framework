package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record SyncShopifyStoreDocumentsRequest(
    String mode,
    List<ShopifyStoreSyncDocument> documents
) {
}
