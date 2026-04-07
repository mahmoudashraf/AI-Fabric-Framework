package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

/**
 * Public summary for a deployment created or inspected through the public provisioning API.
 *
 * <p>The connector remains internal-only. Public consumers should treat
 * {@code connectorBaseUrl} as withheld and rely on {@code integration} for the
 * supported runtime access posture.</p>
 */
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
