package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentArtifactBundleSummary(
    String deploymentId,
    String deploymentVersionId,
    String versionLabel,
    String configHash,
    String actionsArtifactUrl,
    String entityArtifactUrl,
    String routingArtifactUrl,
    String promptArtifactUrl,
    String manifestUrl
) {
}
