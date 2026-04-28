package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeProvisioningStatusSummary(
    String shopDomain,
    String status,
    String phase,
    String nextAction,
    String summaryMessage,
    ShopifyBridgePackageProfileSummary effectiveProfile,
    ShopifyBridgeProvisioningJobSummary latestJob,
    List<ShopifyBridgeProvisioningJobSummary> recentJobs
) {
}
