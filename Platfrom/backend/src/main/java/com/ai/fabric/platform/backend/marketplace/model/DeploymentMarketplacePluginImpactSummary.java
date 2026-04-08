package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record DeploymentMarketplacePluginImpactSummary(
    String pluginId,
    String pluginSlug,
    String pluginDisplayName,
    String pluginType,
    String pluginVersionId,
    String pluginVersion,
    String installMode,
    List<String> affectedConfigKeys,
    List<String> actionIds,
    JsonNode knowledgeSources,
    List<String> shellModuleRefs,
    JsonNode shellDefaults,
    JsonNode config,
    int secretRefCount
) {
}
