package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentWorkspaceLifecycleSummary(
    String savedDraftState,
    String liveState,
    boolean hasPublishedVersion,
    boolean hasLiveVersion,
    boolean savedDraftMatchesLatestPublished,
    boolean liveMatchesLatestPublished,
    String latestPublishedVersionId,
    String latestPublishedVersionLabel,
    Instant latestPublishedAt,
    String liveVersionId,
    String liveVersionLabel,
    Instant liveAppliedAt,
    String summaryMessage
) {
}
