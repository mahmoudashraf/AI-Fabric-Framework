package com.ai.fabric.platform.backend.partner.model;

import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationAutomationSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationIndexedFieldSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationPolicySummary;

import java.time.Instant;
import java.util.List;

public record PartnerShopifyVectorizationOperationsSummary(
    boolean bootstrapped,
    List<String> selectedCategories,
    List<String> selectedEntityTypes,
    List<String> requiredPluginIds,
    List<String> installedPluginIds,
    List<String> missingPluginIds,
    List<String> disabledPluginIds,
    boolean reconciliationRequired,
    boolean connectionConfigured,
    String sourceConnectionStatus,
    boolean planConfigured,
    String planStatus,
    boolean runnerConfigured,
    String runnerRegistrationStatus,
    boolean deploymentApplyInProgress,
    String deploymentApplyStatus,
    String runnerMode,
    String syncState,
    boolean readyToRun,
    List<String> blockingReasons,
    PartnerShopifyVectorizationRunSummary lastRun,
    ShopifyStoreVectorizationPolicySummary policy,
    List<ShopifyStoreVectorizationIndexedFieldSummary> effectiveIndexedFields,
    ShopifyStoreVectorizationAutomationSummary automation,
    List<PartnerShopifyVectorizationEventSummary> recentEvents
) {
    public record PartnerShopifyVectorizationRunSummary(
        String reason,
        String status,
        String requestedStatus,
        List<String> entityScope,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt
    ) {
    }
}
