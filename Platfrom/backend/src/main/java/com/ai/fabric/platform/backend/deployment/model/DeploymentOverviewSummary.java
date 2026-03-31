package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentOverviewSummary(
    String id,
    String name,
    String environment,
    String templateId,
    DeploymentSourceSummary source,
    String status,
    String activeVersion,
    String healthStatus,
    String healthSummary,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    boolean approvalRequiredForApply,
    boolean approvalRequiredForDelete,
    DeploymentLifecycleSnapshotSummary latestRelease,
    DeploymentVerificationSnapshotSummary latestVerification,
    Instant archivedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
