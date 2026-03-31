package com.ai.fabric.platform.backend.security.model;

public record DeploymentWorkspacePreferences(
    String lastDeploymentId,
    String lastSection
) {
}
