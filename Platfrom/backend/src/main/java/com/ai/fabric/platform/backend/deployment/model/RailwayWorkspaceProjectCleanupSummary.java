package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayWorkspaceProjectCleanupSummary(
    String projectId,
    String projectName,
    String ownershipState,
    boolean platformManagedCandidate,
    boolean deletable,
    int totalServiceCount,
    List<RailwayWorkspaceCleanupOwnerSummary> owners,
    List<RailwayWorkspaceOrphanServiceSummary> orphanServices,
    String summaryMessage
) {
}
