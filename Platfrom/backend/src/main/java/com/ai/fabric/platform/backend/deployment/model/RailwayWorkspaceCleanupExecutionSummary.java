package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayWorkspaceCleanupExecutionSummary(
    String status,
    String message,
    int deletedProjectCount,
    int deletedServiceCount,
    List<String> deletedProjectIds,
    List<String> deletedServiceIds,
    List<String> skippedIds
) {
}
