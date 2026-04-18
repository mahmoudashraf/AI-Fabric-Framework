package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeSyncStoreDocumentsRequest(
    String mode,
    List<ShopifyBridgeStoreSyncDocument> documents
) {
}
