package com.ai.fabric.product.shopify.bridge.diagnostics.model;

import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageOverview;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeOverviewResponse(
    String appName,
    String serviceRef,
    String productFamily,
    String serviceKind,
    String environmentScope,
    String platformBaseUrl,
    String publicBaseUrl,
    boolean adminApiKeyConfigured,
    String status,
    Instant serverStartedAt,
    ShopifyBridgeInstallOverview installs,
    ShopifyBridgeStoreOverview stores,
    ShopifyBridgeBillingSummary billing,
    ShopifyBridgeUsageOverview usage,
    List<String> capabilities,
    List<String> notYetImplemented
) {
}
