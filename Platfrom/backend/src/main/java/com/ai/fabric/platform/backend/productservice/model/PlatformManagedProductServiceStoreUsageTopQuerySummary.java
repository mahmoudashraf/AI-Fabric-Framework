package com.ai.fabric.platform.backend.productservice.model;

import java.time.Instant;

public record PlatformManagedProductServiceStoreUsageTopQuerySummary(
    String surfaceId,
    String label,
    String queryText,
    long count,
    Instant lastAskedAt
) {
}
