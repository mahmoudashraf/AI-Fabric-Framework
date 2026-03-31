package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocMigrationGuideSummary(
    String suggestedDatasetLabel,
    int maxRecordsPerRun,
    int maxContentLength,
    String defaultVectorSpace,
    List<String> supportedVectorSpaces,
    List<DeploymentPocMigrationSourceSummary> supportedSources,
    List<DeploymentPocMigrationCheckSummary> readinessChecks,
    List<String> warnings
) {
}
