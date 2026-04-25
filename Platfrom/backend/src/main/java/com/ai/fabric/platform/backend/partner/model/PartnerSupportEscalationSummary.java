package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

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
    List<String> evidenceBundleIds,
    Instant createdAt,
    Instant updatedAt
) {
}
