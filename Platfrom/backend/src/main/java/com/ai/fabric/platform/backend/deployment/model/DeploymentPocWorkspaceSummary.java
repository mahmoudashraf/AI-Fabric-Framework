package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocWorkspaceSummary(
    DeploymentPocDatasetSummary dataset,
    DeploymentPocRuntimeIndexingSummary indexing,
    DeploymentPocResetCapabilities resetCapabilities,
    List<String> warnings
) {
}
