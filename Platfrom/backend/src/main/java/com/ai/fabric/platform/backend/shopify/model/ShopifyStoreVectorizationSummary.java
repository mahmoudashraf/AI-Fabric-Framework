package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreVectorizationSummary(
    String shopDomain,
    String deploymentId,
    boolean bootstrapped,
    List<String> selectedCategories,
    List<String> selectedEntityTypes,
    List<String> requiredPluginIds,
    List<String> installedPluginIds,
    List<String> missingPluginIds,
    List<String> disabledPluginIds,
    boolean reconciliationRequired,
    boolean connectionConfigured,
    String sourceConnectionId,
    String sourceConnectionStatus,
    String sourceAdapterType,
    boolean planConfigured,
    String planId,
    String planStatus,
    boolean runnerConfigured,
    String runnerRegistrationId,
    String runnerRegistrationStatus,
    boolean deploymentApplyInProgress,
    String deploymentApplyStatus,
    String runnerMode,
    String syncState,
    boolean readyToRun,
    List<String> blockingReasons,
    ShopifyStoreVectorizationRunSummary lastRun,
    ShopifyStoreVectorizationPolicySummary policy,
    List<ShopifyStoreVectorizationIndexedFieldSummary> effectiveIndexedFields,
    ShopifyStoreVectorizationAutomationSummary automation,
    List<ShopifyStoreVectorizationEventSummary> recentEvents
) {
}
