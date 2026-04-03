package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentNavigationProviderSummary(
    String provider,
    String mode,
    String projectName,
    String projectId,
    String projectUrl,
    String workspaceId,
    String repository,
    String branch,
    boolean available,
    String summaryMessage
) {
}
