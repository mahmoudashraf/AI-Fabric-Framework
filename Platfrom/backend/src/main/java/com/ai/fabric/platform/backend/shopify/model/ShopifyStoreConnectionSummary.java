package com.ai.fabric.platform.backend.shopify.model;

import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;

import java.time.Instant;

public record ShopifyStoreConnectionSummary(
    String id,
    String shopDomain,
    String displayName,
    String productServiceId,
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
    ShopifyStoreCredentialSummary credentials,
    ShopifyStoreSourcePreflightSummary sourcePreflight,
    ShopifyStoreSyncSummary syncDetail,
    ShopifyStoreWebhookSummary webhookDetail,
    ShopifyStoreWidgetSummary widgetDetail,
    ShopifyStoreCapabilitySummary capabilities,
    ShopifyStoreReadinessSummary readiness,
    DeploymentVersionSummary latestVersion,
    DeploymentReleaseSummary latestRelease,
    Instant lastSourcePreflightAt,
    Instant lastSyncAt,
    Instant lastWebhookAt,
    Instant createdAt,
    Instant updatedAt
) {
}
