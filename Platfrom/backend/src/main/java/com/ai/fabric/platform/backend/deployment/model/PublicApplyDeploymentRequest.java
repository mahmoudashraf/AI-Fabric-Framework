package com.ai.fabric.platform.backend.deployment.model;

public record PublicApplyDeploymentRequest(
    String versionId,
    String targetProfileId,
    String sourceArtifactId
) {
    public PublicApplyDeploymentRequest(String versionId) {
        this(versionId, null, null);
    }
}
