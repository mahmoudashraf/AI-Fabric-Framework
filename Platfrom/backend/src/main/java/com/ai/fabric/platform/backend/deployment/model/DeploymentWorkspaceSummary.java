package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentWorkspaceSummary(
    DeploymentOverviewSummary deployment,
    DeploymentTemplateSummary template,
    DeploymentWorkspaceAccessSummary access,
    DeploymentWorkspaceDraftSummary draft,
    DeploymentWorkspaceLifecycleSummary lifecycle,
    DeploymentVersionSummary latestVersion,
    DeploymentReleaseSummary latestRelease,
    DeploymentVerificationRunSummary latestVerificationRun,
    int versionCount,
    int releaseCount,
    int verificationRunCount
) {
}
