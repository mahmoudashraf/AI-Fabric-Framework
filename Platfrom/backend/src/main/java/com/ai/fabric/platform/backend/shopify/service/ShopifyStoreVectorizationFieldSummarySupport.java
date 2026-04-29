package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationIndexedFieldSummary;

final class ShopifyStoreVectorizationFieldSummarySupport {

    private ShopifyStoreVectorizationFieldSummarySupport() {
    }

    record FieldValue(
        ShopifyStoreVectorizationIndexedFieldSummary field,
        String value
    ) {
    }
}
