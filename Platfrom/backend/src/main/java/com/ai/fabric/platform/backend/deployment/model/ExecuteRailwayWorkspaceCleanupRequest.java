package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record ExecuteRailwayWorkspaceCleanupRequest(
    Boolean confirm,
    String reason,
    List<String> projectIds,
    List<String> serviceIds
) {
}
