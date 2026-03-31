package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocImportRunSummary(
    String id,
    String deploymentId,
    String datasetLabel,
    String sourceType,
    String vectorSpace,
    String status,
    int recordCount,
    int importedCount,
    int failedCount,
    String errorMessage,
    String createdByActorId,
    String createdByDisplayName,
    String createdAt
) {
}
