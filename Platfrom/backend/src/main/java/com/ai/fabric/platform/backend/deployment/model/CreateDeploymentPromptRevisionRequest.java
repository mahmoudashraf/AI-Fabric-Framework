package com.ai.fabric.platform.backend.deployment.model;

public record CreateDeploymentPromptRevisionRequest(
    String revisionLabel,
    String revisionSummary
) {
}
