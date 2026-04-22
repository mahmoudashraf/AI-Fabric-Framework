package com.ai.fabric.platform.backend.marketplace.model;

import jakarta.validation.constraints.NotBlank;

public record CreateMarketplaceTemplateBootstrapRequest(
    String pluginVersion,
    @NotBlank String name,
    @NotBlank String environment,
    String templateId,
    String vectorProvisioningMode,
    String customerId,
    String tenantId
) {
}
