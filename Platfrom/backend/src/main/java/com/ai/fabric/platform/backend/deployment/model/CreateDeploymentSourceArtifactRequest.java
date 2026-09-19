package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateDeploymentSourceArtifactRequest(
    String serviceName,
    String artifactType,
    String imageRepository,
    String imageTag,
    String imageDigest,
    String gitCommitSha,
    String buildRunId,
    String sbomRef,
    String promotionChannel,
    JsonNode capabilityManifest
) {
    public CreateDeploymentSourceArtifactRequest(String serviceName,
                                                 String artifactType,
                                                 String imageRepository,
                                                 String imageTag,
                                                 String imageDigest,
                                                 String gitCommitSha,
                                                 String buildRunId,
                                                 String sbomRef,
                                                 String promotionChannel) {
        this(serviceName, artifactType, imageRepository, imageTag, imageDigest, gitCommitSha,
            buildRunId, sbomRef, promotionChannel, null);
    }
}
