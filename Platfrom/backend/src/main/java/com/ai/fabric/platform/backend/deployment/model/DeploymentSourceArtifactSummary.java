package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

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
    JsonNode capabilityManifest,
    String capabilityManifestHash,
    Instant createdAt,
    Instant promotedAt
) {
}
