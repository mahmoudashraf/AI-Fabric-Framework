package com.ai.fabric.platform.backend.tenant.model;

import java.time.Instant;
import java.util.List;

public record PlatformCustomerSummary(
    String id,
    String name,
    String slug,
    String description,
    String status,
    boolean platformManaged,
    int tenantCount,
    int deploymentCount,
    int consumerCount,
    Instant createdAt,
    Instant updatedAt,
    List<PlatformTenantSummary> tenants,
    List<PlatformConsumerSummary> consumers
) {
}
