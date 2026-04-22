package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;

public record ShopifyStoreLinkedDeploymentSummary(
    String id,
    String name,
    String environment,
    String templateId,
    String status,
    String customerId,
    String tenantId,
    String activeVersionId,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    boolean approvalRequiredForApply,
    boolean approvalRequiredForDelete,
    Instant createdAt,
    Instant updatedAt
) {
}
