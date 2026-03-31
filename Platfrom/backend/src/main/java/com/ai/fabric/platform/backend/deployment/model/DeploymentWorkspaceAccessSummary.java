package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentWorkspaceAccessSummary(
    String assignmentRole,
    boolean canOperate,
    boolean canEdit,
    boolean canAdmin
) {
}
