package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentLifecycleSnapshotSummary(
    String releaseId,
    String versionId,
    String status,
    String provisioningStatus,
    String verificationStatus,
    String currentStepKey,
    String currentStepDescription,
    Instant updatedAt
) {
}
