package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record PublicConsumerDeploymentSummary(
    String consumerId,
    String deploymentId,
    String name,
    String environment,
    String templateId,
    String status,
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
