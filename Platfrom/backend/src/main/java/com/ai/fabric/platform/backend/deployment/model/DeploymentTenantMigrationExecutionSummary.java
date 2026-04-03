package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentTenantMigrationExecutionSummary(
    String sourceDeploymentId,
    String deploymentId,
    String deploymentName,
    String environmentName,
    String customerId,
    String customerName,
    String tenantId,
    String tenantName,
    boolean autoCreatedTenant,
    String status,
    String message
) {
}
