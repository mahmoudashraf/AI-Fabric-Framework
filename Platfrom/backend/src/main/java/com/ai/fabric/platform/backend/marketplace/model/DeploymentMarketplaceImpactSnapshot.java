package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record DeploymentMarketplaceImpactSnapshot(
    String deploymentId,
    int installedPluginCount,
    List<String> affectedConfigKeys,
    List<DeploymentMarketplacePluginImpactSummary> pluginImpacts
) {
}
