package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentTenantBindingSummary(
    String customerId,
    String customerName,
    String customerSlug,
    String customerStatus,
    boolean customerPlatformManaged,
    String tenantId,
    String tenantName,
    String tenantSlug,
    String tenantStatus,
    boolean tenantPlatformManaged,
    boolean mutable,
    int publishedVersionCount,
    int releaseCount,
    String bindingChangeStatus,
    String bindingChangeMessage
) {
}
