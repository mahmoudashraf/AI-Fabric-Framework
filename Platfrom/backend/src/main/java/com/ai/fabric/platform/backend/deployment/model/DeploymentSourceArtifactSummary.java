package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentSourceArtifactSummary(
    String id,
    String serviceName,
    String artifactType,
    String imageRepository,
    String imageTag,
    String imageDigest,
    String imageReference,
    String gitCommitSha,
    String buildRunId,
    String sbomRef,
    String promotionChannel,
    Instant createdAt,
    Instant promotedAt
) {
}
