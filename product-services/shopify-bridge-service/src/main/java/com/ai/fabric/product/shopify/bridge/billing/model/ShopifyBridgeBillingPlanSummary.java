package com.ai.fabric.product.shopify.bridge.billing.model;

import java.util.List;

public record ShopifyBridgeBillingPlanSummary(
    String tierKey,
    String planName,
    String amount,
    String currencyCode,
    String interval,
    boolean active,
    boolean commerciallyAvailable,
    boolean merchantApprovalSupported,
    boolean actionCapable,
    Integer catalogProductCap,
    String syncCadence,
    boolean poweredByBadgeRequired,
    boolean chatFallbackEnabled,
    boolean requiresExplicitConfirmation,
    boolean auditTrailAvailable,
    List<String> actionPackages,
    List<String> allowedSurfaces,
    String message
) {
}
