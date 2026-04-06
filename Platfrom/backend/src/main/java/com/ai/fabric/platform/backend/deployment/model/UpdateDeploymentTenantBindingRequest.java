package com.ai.fabric.platform.backend.deployment.model;

public record UpdateDeploymentTenantBindingRequest(
    String customerId,
    String tenantId
) {
}
