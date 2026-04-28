package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeProvisioningJobSummary(
    String id,
    String shopDomain,
    String storeConnectionId,
    String jobType,
    String status,
    String phase,
    String requestedPackageKey,
    String requestedTierKey,
    String requestedRuntimeProfileKey,
    String requestedVectorProfileKey,
    String previousPackageKey,
    String previousRuntimeProfileKey,
    String previousVectorProfileKey,
    String requestedTemplatePluginId,
    String requestedTemplatePluginVersion,
    List<String> requestedPluginIds,
    String profileChangeStrategy,
    boolean vectorReindexRequired,
    String installIntentId,
    int attemptCount,
    int maxAttempts,
    String lastErrorCode,
    String lastErrorMessage,
    String bootstrapDeploymentId,
    String verificationRunId,
    String nextAction,
    String summaryMessage,
    Instant readyAt,
    Instant failedAt,
    Instant cancelledAt,
    Instant createdAt,
    Instant updatedAt
) {
}
