package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerTemplateSummary(
    String id,
    String name,
    String category,
    String vertical,
    List<String> surfaceIds,
    String bodyMarkdown,
    List<String> assumptions,
    List<String> checklist,
    Instant updatedAt
) {
}
