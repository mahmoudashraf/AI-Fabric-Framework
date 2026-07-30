package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerShopifyProvisioningJobSummary(
    String id,
    String jobType,
    String status,
    String phase,
    String requestedPackageKey,
    String requestedTierKey,
    List<String> requestedPluginIds,
    boolean vectorReindexRequired,
    int attemptCount,
    int maxAttempts,
    String lastErrorCode,
    String nextAction,
    String summaryMessage,
    Instant readyAt,
    Instant failedAt,
    Instant cancelledAt,
    Instant createdAt,
    Instant updatedAt
) {
}
