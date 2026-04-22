package com.ai.fabric.platform.backend.shopify.service;

import java.util.List;

record ShopifyStoreVectorizationSourcePageSnapshot(
    String entityType,
    int totalCount,
    boolean hasMore,
    String nextCursor,
    List<ShopifyStoreVectorizationSourceRecordSnapshot> items
) {
}
