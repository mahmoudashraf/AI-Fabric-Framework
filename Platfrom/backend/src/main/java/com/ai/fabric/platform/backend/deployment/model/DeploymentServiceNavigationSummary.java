package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentServiceNavigationSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    DeploymentNavigationProviderSummary provider,
    List<DeploymentNavigationSurfaceSummary> surfaces,
    List<DeploymentNavigationRelationshipSummary> relationships,
    String summaryMessage
) {
}
