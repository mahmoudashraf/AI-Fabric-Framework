package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentSourceSummary(
    String repository,
    String branch,
    String repositoryOverride,
    String branchOverride,
    boolean overrideActive
) {
}
