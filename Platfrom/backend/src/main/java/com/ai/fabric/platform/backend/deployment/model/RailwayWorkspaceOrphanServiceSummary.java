package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayWorkspaceOrphanServiceSummary(
    String serviceId,
    String serviceName,
    String projectId,
    String projectName,
    String ownershipState,
    boolean platformManagedCandidate,
    boolean deletable,
    String sourceRepository,
    String sourceBranch,
    List<RailwayWorkspaceCleanupOwnerSummary> owners,
    String summaryMessage
) {
}
