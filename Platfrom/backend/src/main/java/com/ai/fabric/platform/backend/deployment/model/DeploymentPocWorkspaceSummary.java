package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocWorkspaceSummary(
    DeploymentPocDatasetSummary dataset,
    DeploymentPocRuntimeIndexingSummary indexing,
    DeploymentPocMigrationGuideSummary migration,
    DeploymentPocResetCapabilities resetCapabilities,
    List<DeploymentPocImportRunSummary> recentImports,
    List<String> warnings
) {
}
