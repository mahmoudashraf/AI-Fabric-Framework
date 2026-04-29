package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerTemplateApplicationSummary(
    String id,
    String templateId,
    String templateName,
    String category,
    String storeAssignmentId,
    String shopDomain,
    String status,
    List<String> checklist,
    List<String> assumptions,
    Instant appliedAt,
    Instant updatedAt
) {
}
