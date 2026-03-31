package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentConfigDiffCenterSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    DeploymentConfigReferenceSummary draft,
    DeploymentConfigReferenceSummary latestPublished,
    DeploymentConfigReferenceSummary live,
    DeploymentConfigTemplateSourceSummary templateSource,
    List<DeploymentConfigSectionDiffSummary> sections,
    String summaryMessage
) {
}
