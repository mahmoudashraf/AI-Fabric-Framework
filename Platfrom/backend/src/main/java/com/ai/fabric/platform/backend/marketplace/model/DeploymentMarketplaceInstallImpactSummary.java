package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record DeploymentMarketplaceInstallImpactSummary(
    String installId,
    String pluginId,
    String pluginDisplayName,
    String pluginType,
    String pluginVersion,
    List<String> actionIds,
    List<String> knowledgeSourceIds,
    List<String> shellModuleIds,
    List<String> shellCardIds
) {
}
