package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreLinkedConsumerSummary(
    String consumerId,
    String customerId,
    String displayName,
    String status,
    String boundDeploymentId,
    String boundDeploymentName,
    String boundDeploymentEnvironment,
    String boundDeploymentStatus,
    Instant lastBoundAt,
    Instant createdAt,
    Instant updatedAt
) {
}
