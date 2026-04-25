package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerSupportEscalationSummary(
    String id,
    String storeAssignmentId,
    String shopDomain,
    String title,
    String severity,
    String status,
    String nextAction,
    Instant dueAt,
    String description,
    String resolutionSummary,
    Instant createdAt,
    Instant updatedAt
) {
}
