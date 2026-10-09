package com.ai.fabric.platform.backend.aiworkspace.model;

import java.util.List;

public record AIWorkspaceReadinessSummary(
    boolean ready,
    String installationId,
    String connectionMode,
    String consumerId,
    String deploymentId,
    String releaseId,
    String runtimeBaseUrl,
    String assignmentRevision,
    List<AIWorkspaceReadinessCheck> checks
) {
}
