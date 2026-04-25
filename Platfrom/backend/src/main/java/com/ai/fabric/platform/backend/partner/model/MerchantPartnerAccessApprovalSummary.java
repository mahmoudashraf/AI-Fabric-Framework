package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record MerchantPartnerAccessApprovalSummary(
    String assignmentId,
    String shopDomain,
    String status,
    Instant approvedAt
) {
}
