package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerEligibleStoreSummary(
    String storeConnectionId,
    String shopDomain,
    String displayName,
    String installStatus,
    String knowledgeSyncStatus,
    String readinessStatus,
    String widgetStatus,
    Instant lastActivityAt,
    List<String> enabledSourceCategories
) {
}
