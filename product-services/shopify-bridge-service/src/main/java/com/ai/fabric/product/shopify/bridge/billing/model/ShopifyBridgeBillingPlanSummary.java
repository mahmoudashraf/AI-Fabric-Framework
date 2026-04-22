package com.ai.fabric.product.shopify.bridge.billing.model;

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
    String message
) {
}
