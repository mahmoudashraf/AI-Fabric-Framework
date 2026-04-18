package com.ai.fabric.product.shopify.bridge.diagnostics.model;

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
    List<String> capabilities,
    List<String> notYetImplemented
) {
}
