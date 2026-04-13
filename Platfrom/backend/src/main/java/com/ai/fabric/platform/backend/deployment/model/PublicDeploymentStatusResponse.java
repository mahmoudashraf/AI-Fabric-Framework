package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

/**
 * Public operational status response for a deployment bound to a public API client.
 *
 * <p>The connector remains internal-only. Public consumers should rely on
 * {@code integration} for the supported runtime access posture.</p>
 */
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
    PublicDeploymentAccessSummary access,
    PublicDeploymentIntegrationSummary integration,
    DeploymentLifecycleSnapshotSummary latestRelease,
    DeploymentVerificationSnapshotSummary latestVerification,
    Instant createdAt,
    Instant updatedAt
) {
}
