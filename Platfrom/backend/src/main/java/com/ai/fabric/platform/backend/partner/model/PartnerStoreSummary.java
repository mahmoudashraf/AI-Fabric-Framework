package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerStoreSummary(
    String id,
    String shopDomain,
    String merchantName,
    String plan,
    String status,
    List<String> enabledSurfaces,
    String knowledgeSyncStatus,
    String readinessStatus,
    String topBlocker,
    Instant lastActivityAt,
    String assignmentStatus
) {
}
