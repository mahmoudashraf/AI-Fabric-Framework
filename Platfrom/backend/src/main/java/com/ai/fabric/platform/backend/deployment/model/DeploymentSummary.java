package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentSummary(
    String id,
    String name,
    String environment,
    String templateId,
    DeploymentTenantBindingSummary binding,
    DeploymentSourceSummary source,
    String status,
    String activeVersion,
    String runtimeBaseUrl,
    boolean connectorProvisioned,
    boolean approvalRequiredForApply,
    boolean approvalRequiredForDelete,
    Instant createdAt
) {
}
