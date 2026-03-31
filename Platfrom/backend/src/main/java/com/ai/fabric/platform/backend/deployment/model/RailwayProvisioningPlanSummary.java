package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayProvisioningPlanSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    String templateId,
    String versionId,
    String versionLabel,
    String configHash,
    String mode,
    String projectName,
    String repository,
    String branch,
    String workspaceId,
    String artifactStrategy,
    RailwayArtifactUrlsSummary artifactUrls,
    RailwayProvisioningServicesSummary services,
    List<RailwayProvisioningStepSummary> steps
) {
}
