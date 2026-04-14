package com.ai.fabric.platform.backend.marketplace.model;

public record DeploymentMarketplaceInstallResolutionSummary(
    DeploymentMarketplaceInstallSummary install,
    DeploymentMarketplaceImpactSummary impact
) {
}
