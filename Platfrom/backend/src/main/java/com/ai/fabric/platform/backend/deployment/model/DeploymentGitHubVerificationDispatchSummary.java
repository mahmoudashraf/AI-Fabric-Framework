package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentGitHubVerificationDispatchSummary(
    String deploymentId,
    String releaseId,
    String deploymentVersionId,
    String repository,
    String branch,
    String workflowFile,
    String profile,
    boolean verifyWrite,
    String summaryMessage,
    DeploymentGitHubVerificationRunSummary run
) {
}
