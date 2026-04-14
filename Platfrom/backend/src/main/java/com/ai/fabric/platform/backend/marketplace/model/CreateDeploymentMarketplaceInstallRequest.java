package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record CreateDeploymentMarketplaceInstallRequest(
    @NotBlank String pluginId,
    @NotBlank String pluginVersion,
    JsonNode config,
    JsonNode secretRefs
) {
}
