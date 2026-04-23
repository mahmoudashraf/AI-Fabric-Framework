package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgeStoreSummary(
    String id,
    String shopDomain,
    String displayName,
    String productServiceRef,
    String productServiceDisplayName,
    String customerId,
    String customerName,
    String deploymentId,
    String deploymentName,
    String deploymentStatus,
    String consumerId,
    String consumerDisplayName,
    String installStatus,
    String syncStatus,
    String sourceReadinessStatus,
    String widgetStatus,
    String onboardingStatus,
    boolean productsEnabled,
    boolean collectionsEnabled,
    boolean pagesEnabled,
    boolean policiesEnabled,
    boolean articlesEnabled,
    ShopifyBridgeStoreCredentialSummary credentials,
    ShopifyBridgeStoreSourcePreflightSummary sourcePreflight,
    ShopifyBridgeStoreSyncSummary syncDetail,
    ShopifyBridgeStoreWebhookSummary webhookDetail,
    ShopifyBridgeStoreWidgetSummary widgetDetail,
    ShopifyBridgeStoreCapabilitySummary capabilities,
    ShopifyBridgeStoreReadinessSummary readiness,
    ShopifyBridgeStoreDeploymentVersionSummary latestVersion,
    ShopifyBridgeStoreDeploymentReleaseSummary latestRelease,
    Instant lastSourcePreflightAt,
    Instant lastSyncAt,
    Instant lastWebhookAt,
    Instant createdAt,
    Instant updatedAt
) {
}
