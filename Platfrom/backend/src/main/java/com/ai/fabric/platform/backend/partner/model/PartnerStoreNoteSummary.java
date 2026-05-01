package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerStoreNoteSummary(
    String id,
    String storeAssignmentId,
    String authorName,
    String bodyMarkdown,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
