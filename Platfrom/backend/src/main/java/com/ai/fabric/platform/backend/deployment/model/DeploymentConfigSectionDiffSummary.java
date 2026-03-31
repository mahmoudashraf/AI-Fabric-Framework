package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentConfigSectionDiffSummary(
    String key,
    String label,
    String draftValue,
    String latestPublishedValue,
    String liveValue,
    boolean draftMatchesLatestPublished,
    boolean draftMatchesLive,
    boolean liveMatchesLatestPublished,
    String driftState,
    String summaryMessage
) {
}
