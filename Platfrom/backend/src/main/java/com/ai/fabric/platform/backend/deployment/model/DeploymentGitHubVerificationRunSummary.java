package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentGitHubVerificationRunSummary(
    long runId,
    String workflowFile,
    String displayTitle,
    String status,
    String conclusion,
    String htmlUrl,
    String branch,
    String event,
    Instant createdAt,
    Instant updatedAt
) {
}
