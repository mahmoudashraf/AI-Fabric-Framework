package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record PublicDeploymentSummary(
    String clientId,
    String externalDeploymentKey,
    String deploymentId,
    boolean created,
    String name,
    String environment,
    String templateId,
    String status,
    String activeVersion,
    String latestPublishedVersionId,
    String latestPublishedVersionLabel,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    PublicDeploymentAccessSummary access,
    PublicDeploymentIntegrationSummary integration,
    DeploymentLifecycleSnapshotSummary latestRelease,
    DeploymentVerificationSnapshotSummary latestVerification,
    Instant createdAt,
    Instant updatedAt
) {
}
