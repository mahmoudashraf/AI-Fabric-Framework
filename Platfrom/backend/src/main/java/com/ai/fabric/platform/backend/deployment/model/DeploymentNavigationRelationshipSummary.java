package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentNavigationRelationshipSummary(
    String key,
    String fromLabel,
    String toLabel,
    String flowType,
    String summaryMessage
) {
}
