package com.ai.fabric.platform.backend.marketplace.model;

import java.time.Instant;

public record MarketplacePublisherSummary(
    String id,
    String slug,
    String displayName,
    String contactEmail,
    String ownerUserId,
    String verificationStatus,
    String status,
    int submissionCount,
    Instant createdAt,
    Instant updatedAt
) {
}
