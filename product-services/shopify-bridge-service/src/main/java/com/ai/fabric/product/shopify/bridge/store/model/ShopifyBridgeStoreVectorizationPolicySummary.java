package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeStoreVectorizationPolicySummary(
    long policyVersion,
    boolean autoIndexingDefault,
    List<ShopifyBridgeStoreVectorizationSourcePolicySummary> sourcePolicies,
    String updatedBy,
    Instant updatedAt
) {
}
