package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record MerchantPartnerAccessInviteSummary(
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
