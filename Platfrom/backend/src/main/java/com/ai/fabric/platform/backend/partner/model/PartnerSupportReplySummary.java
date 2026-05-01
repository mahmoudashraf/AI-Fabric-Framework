package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerSupportReplySummary(
    String id,
    String authorName,
    String authorRole,
    String visibility,
    String bodyMarkdown,
    List<String> attachments,
    Instant createdAt
) {
}
