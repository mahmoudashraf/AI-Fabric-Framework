package com.ai.fabric.platform.backend.shopify.service;

import java.util.Map;

record ShopifyStoreVectorizationSourceRecordSnapshot(
    String id,
    String sourceCategory,
    String entityType,
    String sourceRecordVersion,
    Map<String, Object> values
) {
}
