package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record MerchantPartnerAccessDecisionSummary(
    String requestId,
    String assignmentId,
    String shopDomain,
    String status,
    Instant decidedAt
) {
}
