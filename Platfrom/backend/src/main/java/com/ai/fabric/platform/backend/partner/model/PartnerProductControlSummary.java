package com.ai.fabric.platform.backend.partner.model;

import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWidgetSettingsSummary;

import java.time.Instant;
import java.util.List;

public record PartnerProductControlSummary(
    String storeId,
    String storeConnectionId,
    String shopDomain,
    String merchantName,
    String assignmentStatus,
    String installStatus,
    String widgetStatus,
    String knowledgeSyncStatus,
    String readinessStatus,
    PartnerProductSourceSettingsSummary sourceSettings,
    List<String> enabledSurfaces,
    ShopifyStoreWidgetSettingsSummary widgetSettings,
    ShopifyStoreSupportProfileSummary supportProfile,
    List<String> capabilities,
    PartnerPackageTrialActivationSummary activePackageTrial,
    List<PartnerPackageTrialActivationSummary> packageTrialHistory,
    List<String> trialActivationTiers,
    Integer maxTrialDays,
    Instant updatedAt
) {
}
