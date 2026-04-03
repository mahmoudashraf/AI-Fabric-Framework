package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record CreateDeploymentTenantMigrationRequest(
    String customerId,
    String tenantId,
    String proposedDeploymentName,
    String proposedEnvironmentName,
    @NotBlank(message = "reason is required")
    String reason
) {
}
