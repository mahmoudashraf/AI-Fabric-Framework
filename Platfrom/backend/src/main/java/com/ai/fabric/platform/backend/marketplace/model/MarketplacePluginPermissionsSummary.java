package com.ai.fabric.platform.backend.marketplace.model;

public record MarketplacePluginPermissionsSummary(
    boolean contributesTemplate,
    boolean contributesActions,
    boolean contributesKnowledgeSources,
    boolean contributesAutomation,
    boolean contributesShellPresentation,
    boolean contributesSurfaceCapabilities,
    boolean contributesPolicyLogicCapabilities,
    boolean contributesAnalyticsEventCapabilities,
    boolean requiresExternalHttpExecution,
    boolean requiresSharedDatasetAccess,
    boolean requiresDeploymentSecrets
) {
}
