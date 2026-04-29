package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;
import java.util.List;

public record ShopifyStoreVectorizationPolicySummary(
    long policyVersion,
    boolean autoIndexingDefault,
    List<ShopifyStoreVectorizationSourcePolicySummary> sourcePolicies,
    String updatedBy,
    Instant updatedAt
) {
}
