package com.ai.fabric.platform.backend.marketplace.model;

public record MarketplacePluginPermissionsSummary(
    boolean contributesTemplate,
    boolean contributesActions,
    boolean contributesKnowledgeSources,
    boolean contributesProviders,
    boolean contributesShellPresentation,
    boolean requiresExternalHttpExecution,
    boolean requiresSharedDatasetAccess,
    boolean requiresDeploymentSecrets
) {
}
