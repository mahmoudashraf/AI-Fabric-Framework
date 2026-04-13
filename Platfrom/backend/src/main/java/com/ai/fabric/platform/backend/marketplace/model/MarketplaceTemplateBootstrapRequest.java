package com.ai.fabric.platform.backend.marketplace.model;

import jakarta.validation.constraints.NotBlank;

public record MarketplaceTemplateBootstrapRequest(
    @NotBlank String name,
    @NotBlank String environment,
    @NotBlank String templateId,
    String pluginVersionId,
    String vectorProvisioningMode,
    String customerId,
    String tenantId
) {
}
