package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentWorkspaceSummary(
    DeploymentOverviewSummary deployment,
    DeploymentTemplateSummary template,
    DeploymentWorkspaceDraftSummary draft,
    DeploymentVersionSummary latestVersion,
    DeploymentReleaseSummary latestRelease,
    DeploymentVerificationRunSummary latestVerificationRun,
    int versionCount,
    int releaseCount,
    int verificationRunCount
) {
}
