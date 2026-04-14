package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record MarketplacePluginCompatibilitySummary(
    String minPlatformVersion,
    String maxPlatformVersion,
    List<String> requiredCapabilities,
    List<String> supportedDeploymentTargets,
    List<String> supportedAuthModes,
    List<String> supportedProviderModes
) {
}
