package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;

public record ShopifyBridgePartnerAccessInviteSummary(
    String requestId,
    String implementationRequestId,
    String shopDomain,
    String recipientEmail,
    String status,
    String channel,
    String message,
    String approvalUrl,
    Instant sentAt,
    int inviteCount
) {
}
