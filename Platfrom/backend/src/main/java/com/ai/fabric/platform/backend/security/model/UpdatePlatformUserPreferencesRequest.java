package com.ai.fabric.platform.backend.security.model;

public record UpdatePlatformUserPreferencesRequest(
    DeploymentListViewPreferences deploymentListView,
    DeploymentWorkspacePreferences deploymentWorkspace
) {
}
