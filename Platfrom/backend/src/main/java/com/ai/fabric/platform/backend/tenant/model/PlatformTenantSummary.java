package com.ai.fabric.platform.backend.tenant.model;

import java.time.Instant;

public record PlatformTenantSummary(
    String id,
    String customerId,
    String customerName,
    String name,
    String slug,
    String description,
    String status,
    boolean platformManaged,
    String boundDeploymentId,
    String boundDeploymentName,
    String boundDeploymentEnvironment,
    PlatformTenantSharedVectorSummary sharedVector,
    Instant createdAt,
    Instant updatedAt
) {
}
