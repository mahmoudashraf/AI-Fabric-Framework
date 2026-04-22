package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateDeploymentMarketplaceInstallRequest(
    String pluginVersion,
    String status,
    JsonNode config,
    JsonNode secretRefs
) {
}
