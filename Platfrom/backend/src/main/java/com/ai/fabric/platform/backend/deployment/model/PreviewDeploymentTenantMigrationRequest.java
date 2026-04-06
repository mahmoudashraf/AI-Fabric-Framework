package com.ai.fabric.platform.backend.deployment.model;

public record PreviewDeploymentTenantMigrationRequest(
    String customerId,
    String tenantId,
    String proposedDeploymentName,
    String proposedEnvironmentName
) {
}
