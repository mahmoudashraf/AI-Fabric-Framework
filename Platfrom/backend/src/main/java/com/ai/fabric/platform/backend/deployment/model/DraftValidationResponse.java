package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;
import java.util.List;

public record DraftValidationResponse(
    String draftId,
    String deploymentId,
    boolean publishReady,
    int errorCount,
    int warningCount,
    Instant validatedAt,
    List<DraftValidationIssue> issues
) {
}
