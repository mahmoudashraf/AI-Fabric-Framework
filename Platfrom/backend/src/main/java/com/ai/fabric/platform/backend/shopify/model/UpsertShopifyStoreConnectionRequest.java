package com.ai.fabric.platform.backend.shopify.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertShopifyStoreConnectionRequest(
    @NotBlank @Size(min = 3, max = 255) String shopDomain,
    @Size(max = 255) String displayName,
    @NotBlank @Size(min = 3, max = 128) String productServiceRef,
    @Size(max = 64) String customerId,
    @Size(max = 64) String deploymentId,
    @Size(max = 128) String consumerId,
    @Size(max = 64) String installStatus,
    @Size(max = 64) String syncStatus,
    @Size(max = 64) String sourceReadinessStatus,
    @Size(max = 64) String widgetStatus,
    @Size(max = 64) String onboardingStatus,
    Boolean productsEnabled,
    Boolean collectionsEnabled,
    Boolean pagesEnabled,
    Boolean policiesEnabled
) {
}
