package com.ai.fabric.platform.backend.security.model;

public record PlatformUserPreferences(
    DeploymentListViewPreferences deploymentListView,
    DeploymentWorkspacePreferences deploymentWorkspace
) {
}
