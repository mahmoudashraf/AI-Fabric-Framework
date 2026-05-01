package com.ai.fabric.product.shopify.bridge.install.model;

import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;

import java.util.List;
import java.time.Instant;

public record ShopifyInstallRecordSummary(
    String shopDomain,
    String status,
    String shopUrl,
    String userId,
    String appBridgeHost,
    String accessTokenSecretRef,
    String refreshTokenSecretRef,
    String scopesText,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt,
    boolean appScopesUpdateWebhookReady,
    Instant appScopesUpdateWebhookCheckedAt,
    String billingTierKey,
    String billingStatus,
    List<ShopifyBridgeSupportSubscriptionSummary> activeSubscriptions,
    Instant billingCheckedAt,
    Instant installedAt,
    Instant lastAuthenticatedAt,
    Instant lastUninstalledAt
) {
}
