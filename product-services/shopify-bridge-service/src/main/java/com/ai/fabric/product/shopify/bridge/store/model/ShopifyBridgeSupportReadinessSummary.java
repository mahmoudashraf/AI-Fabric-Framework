package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeSupportReadinessSummary(
    String shopDomain,
    String status,
    String message,
    boolean orderLookupSupported,
    boolean orderLookupScopeGranted,
    boolean allOrdersScopeGranted,
    boolean appScopesUpdateWebhookReady,
    boolean installRecoveryRequired,
    String installRecoveryUrl,
    String installStatus,
    String billingTier,
    String billingStatus,
    List<String> grantedScopes,
    List<String> missingScopes,
    List<String> activeSubscriptionNames,
    List<String> verificationMethods,
    List<String> supportedCapabilities,
    List<String> blockedCapabilities
) {
}
