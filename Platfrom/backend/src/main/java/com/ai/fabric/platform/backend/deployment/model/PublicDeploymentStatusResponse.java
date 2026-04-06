package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record PublicDeploymentStatusResponse(
    String clientId,
    String externalDeploymentKey,
    String deploymentId,
    String status,
    String healthStatus,
    String healthSummary,
    String activeVersion,
    String latestPublishedVersionId,
    String latestPublishedVersionLabel,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    PublicDeploymentAccessSummary access,
    DeploymentLifecycleSnapshotSummary latestRelease,
    DeploymentVerificationSnapshotSummary latestVerification,
    Instant createdAt,
    Instant updatedAt
) {
}
