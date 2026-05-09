package com.ai.fabric.platform.backend.deployment.model;

public record CreateDeploymentSourceArtifactRequest(
    String serviceName,
    String artifactType,
    String imageRepository,
    String imageTag,
    String imageDigest,
    String gitCommitSha,
    String buildRunId,
    String sbomRef,
    String promotionChannel
) {
}
