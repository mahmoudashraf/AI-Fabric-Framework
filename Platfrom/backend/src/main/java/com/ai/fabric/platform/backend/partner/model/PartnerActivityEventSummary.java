package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.Map;

public record PartnerActivityEventSummary(
    String id,
    String action,
    String targetType,
    String targetId,
    String result,
    Map<String, Object> details,
    Instant createdAt
) {
}
