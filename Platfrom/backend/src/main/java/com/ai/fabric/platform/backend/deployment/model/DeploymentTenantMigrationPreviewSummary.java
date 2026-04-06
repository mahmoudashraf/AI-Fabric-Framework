package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentTenantMigrationPreviewSummary(
    String sourceDeploymentId,
    String sourceDeploymentName,
    String sourceEnvironmentName,
    String sourceCustomerId,
    String sourceCustomerName,
    String sourceTenantId,
    String sourceTenantName,
    String targetCustomerId,
    String targetCustomerName,
    String targetTenantId,
    String targetTenantName,
    boolean autoCreatesTenant,
    String proposedDeploymentName,
    String proposedEnvironmentName,
    int publishedVersionCount,
    int releaseCount,
    String sourceConfigStrategy,
    boolean sharedVectorScopePresent,
    String sharedVectorStatus,
    String sharedVectorMessage,
    String rollbackPosture,
    String status,
    String message
) {
}
