package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreDeploymentReleaseSummary(
    String id,
    String deploymentVersionId,
    String status,
    String verificationStatus,
    String provisioningStatus,
    String currentStepKey,
    String currentStepDescription,
    String errorMessage,
    Instant createdAt,
    Instant appliedAt,
    Instant updatedAt
) {
}
