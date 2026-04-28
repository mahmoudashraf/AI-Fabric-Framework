package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreProvisioningStatusSummary(
    String shopDomain,
    String status,
    String phase,
    String nextAction,
    String summaryMessage,
    ShopifyCompanionPackageProfileSummary effectiveProfile,
    ShopifyStoreProvisioningJobSummary latestJob,
    List<ShopifyStoreProvisioningJobSummary> recentJobs
) {
}
