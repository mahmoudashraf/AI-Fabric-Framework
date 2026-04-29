package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerStoreAccessLinkSummary(
    String requestId,
    String implementationRequestId,
    String approvalUrl,
    String status,
    Instant expiresAt
) {
}
