package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentSourceOfTruthSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    DeploymentConfigTemplateSourceSummary templateSource,
    DeploymentConfigReferenceSummary draft,
    DeploymentConfigReferenceSummary latestPublished,
    DeploymentConfigReferenceSummary live,
    DeploymentReleaseSummary latestRelease,
    DeploymentArtifactBundleSummary latestPublishedArtifacts,
    DeploymentArtifactBundleSummary liveArtifacts,
    DeploymentSourceOfTruthGeneratedSummary generated,
    DeploymentRailwayLiveReadbackSummary liveRailwayReadback,
    String summaryMessage
) {
}
