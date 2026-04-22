package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record PublicConsumerDeploymentStatusResponse(
    String consumerId,
    String deploymentId,
    String status,
    String healthStatus,
    String healthSummary,
    String activeVersion,
    String latestPublishedVersionId,
    String latestPublishedVersionLabel,
    String runtimeBaseUrl,
    PublicDeploymentAccessSummary access,
    PublicDeploymentIntegrationSummary integration,
    DeploymentLifecycleSnapshotSummary latestRelease,
    DeploymentVerificationSnapshotSummary latestVerification,
    Instant createdAt,
    Instant updatedAt
) {
}
