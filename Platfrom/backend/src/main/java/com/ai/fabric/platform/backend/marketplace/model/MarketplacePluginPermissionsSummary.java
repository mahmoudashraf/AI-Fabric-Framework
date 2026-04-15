package com.ai.fabric.platform.backend.marketplace.model;

public record MarketplacePluginPermissionsSummary(
    boolean contributesTemplate,
    boolean contributesActions,
    boolean contributesKnowledgeSources,
    boolean contributesShellPresentation,
    boolean requiresExternalHttpExecution,
    boolean requiresSharedDatasetAccess,
    boolean requiresDeploymentSecrets
) {
}
