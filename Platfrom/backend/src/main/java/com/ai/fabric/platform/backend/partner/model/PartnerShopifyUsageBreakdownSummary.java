package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;

public record PartnerShopifyUsageBreakdownSummary(
    String key,
    String label,
    String queryText,
    Long count,
    Instant lastAskedAt,
    String summary
) {
}
