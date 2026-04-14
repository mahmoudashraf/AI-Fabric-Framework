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
    String knowledgeSourceArtifactUrl,
    String shellArtifactUrl,
    String automationArtifactUrl,
    String manifestUrl
) {
    public DeploymentArtifactBundleSummary(String deploymentId,
                                           String deploymentVersionId,
                                           String versionLabel,
                                           String configHash,
                                           String actionsArtifactUrl,
                                           String entityArtifactUrl,
                                           String routingArtifactUrl,
                                           String promptArtifactUrl,
                                           String manifestUrl) {
        this(
            deploymentId,
            deploymentVersionId,
            versionLabel,
            configHash,
            actionsArtifactUrl,
            entityArtifactUrl,
            routingArtifactUrl,
            promptArtifactUrl,
            null,
            null,
            null,
            manifestUrl
        );
    }

    public DeploymentArtifactBundleSummary(String deploymentId,
                                           String deploymentVersionId,
                                           String versionLabel,
                                           String configHash,
                                           String actionsArtifactUrl,
                                           String entityArtifactUrl,
                                           String routingArtifactUrl,
                                           String promptArtifactUrl,
                                           String knowledgeSourceArtifactUrl,
                                           String shellArtifactUrl,
                                           String manifestUrl) {
        this(
            deploymentId,
            deploymentVersionId,
            versionLabel,
            configHash,
            actionsArtifactUrl,
            entityArtifactUrl,
            routingArtifactUrl,
            promptArtifactUrl,
            knowledgeSourceArtifactUrl,
            shellArtifactUrl,
            null,
            manifestUrl
        );
    }
}
