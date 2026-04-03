package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentPromptRevisionSummary(
    String id,
    String deploymentId,
    String sourceDraftId,
    String revisionLabel,
    String revisionSummary,
    String createdByActorId,
    String createdByDisplayName,
    int populatedPromptCount,
    Instant createdAt
) {
}
