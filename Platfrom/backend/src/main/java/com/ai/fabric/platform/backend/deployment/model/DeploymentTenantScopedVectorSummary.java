package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentTenantScopedVectorSummary(
    String status,
    String vectorStrategy,
    String vectorProvisioningMode,
    String vectorStoragePosture,
    boolean sharedStorage,
    String lifecycleOwner,
    String customerId,
    String customerName,
    String tenantId,
    String tenantName,
    String scopeType,
    String rootResourceLabel,
    String rootResourceValue,
    String scopePrefix,
    String tenantHandle,
    String scopePattern,
    boolean migrationLocked,
    String migrationMessage,
    String backupRestorePosture,
    DeploymentTenantScopedVectorRegistrySummary registry,
    String summaryMessage
) {
}
