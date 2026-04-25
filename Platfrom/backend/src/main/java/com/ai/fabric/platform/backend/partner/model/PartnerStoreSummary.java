package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerStoreSummary(
    String id,
    String storeConnectionId,
    String shopDomain,
    String merchantName,
    String plan,
    String status,
    List<String> enabledSurfaces,
    String knowledgeSyncStatus,
    String readinessStatus,
    String topBlocker,
    Instant lastActivityAt,
    String assignmentStatus,
    String assignmentSource,
    String approvedBy,
    Instant approvedAt,
    Instant revokedAt,
    Instant assignmentCreatedAt,
    Instant assignmentUpdatedAt,
    String installStatus,
    String widgetStatus,
    Instant lastSyncAt,
    Instant lastWebhookAt,
    List<String> enabledSourceCategories,
    List<String> permissions
) {
}
