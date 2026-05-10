package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerProductionPromotionSummary(
    String storeId,
    String shopDomain,
    String status,
    String message,
    String onboardingStatus,
    String latestReleaseStatus,
    String latestReleaseVerificationStatus,
    List<String> blockers,
    List<String> nextActions,
    Instant requestedAt
) {
}
