package com.ai.fabric.platform.backend.partner.gateway;

import java.time.Instant;

public interface PartnerNotificationGateway {

    PartnerNotificationDeliverySummary sendMerchantAccessInvite(MerchantAccessInviteMessage message);

    PartnerNotificationDeliverySummary notifyPartnerAccessDecision(PartnerAccessDecisionNotification message);

    record MerchantAccessInviteMessage(
        String recipientEmail,
        String shopDomain,
        String partnerName,
        String clientName,
        String approvalUrl,
        Instant expiresAt
    ) {
    }

    record PartnerAccessDecisionNotification(
        String recipientEmail,
        String shopDomain,
        String clientName,
        String status,
        String message
    ) {
    }

    record PartnerNotificationDeliverySummary(
        String status,
        String channel,
        String recipientEmail,
        String providerMessage,
        Instant deliveredAt
    ) {
    }
}
