package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record DeploymentMarketplaceImpactSummary(
    String deploymentId,
    int totalInstalls,
    int actionPluginCount,
    int dataPluginCount,
    int templatePluginCount,
    int automationPluginCount,
    int inferenceProfilePluginCount,
    List<String> installedPluginIds,
    List<String> actionIds,
    List<String> knowledgeSourceIds,
    List<String> shellModuleIds,
    List<String> shellCardIds,
    List<String> automationWorkflowIds,
    List<String> inferenceProfileIds,
    List<String> inferenceEndpointProfileRefs,
    List<DeploymentMarketplaceInstallImpactSummary> installs,
    List<String> recommendedPluginIds,
    List<String> warnings
) {
}
