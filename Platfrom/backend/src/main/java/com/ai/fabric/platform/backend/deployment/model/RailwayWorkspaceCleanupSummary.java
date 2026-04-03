package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayWorkspaceCleanupSummary(
    boolean available,
    String status,
    String workspaceId,
    String workspaceName,
    int projectCount,
    int orphanProjectCount,
    int orphanServiceCount,
    List<RailwayWorkspaceProjectCleanupSummary> projects,
    String summaryMessage
) {
}
