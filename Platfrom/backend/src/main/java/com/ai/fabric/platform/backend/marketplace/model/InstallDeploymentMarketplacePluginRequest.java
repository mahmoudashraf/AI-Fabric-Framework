package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

public record InstallDeploymentMarketplacePluginRequest(
    String pluginVersionId,
    JsonNode config,
    JsonNode secretRefs
) {
}
