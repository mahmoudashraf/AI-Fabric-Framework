package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentOverviewSummary(
    String id,
    String name,
    String environment,
    String templateId,
    DeploymentTenantBindingSummary binding,
    DeploymentSourceSummary source,
    DeploymentWorkspaceAccessSummary access,
    String status,
    String activeVersion,
    String healthStatus,
    String healthSummary,
    String runtimeBaseUrl,
    boolean connectorProvisioned,
    boolean approvalRequiredForApply,
    boolean approvalRequiredForDelete,
    DeploymentLifecycleSnapshotSummary latestRelease,
    DeploymentVerificationSnapshotSummary latestVerification,
    DeploymentDeletionStatusSummary deletion,
    Instant archivedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
