package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerShopifyBillingSummary(
    String mode,
    String tierKey,
    String planName,
    String status,
    String subscriptionName,
    boolean merchantApprovalRequired,
    boolean launchBlocked,
    boolean paidTier,
    boolean actionCapable,
    List<String> allowedSurfaces,
    Instant recordedAt,
    String reason
) {
}
