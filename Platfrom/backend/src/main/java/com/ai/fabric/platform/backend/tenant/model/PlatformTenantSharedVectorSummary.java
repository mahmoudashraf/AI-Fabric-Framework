package com.ai.fabric.platform.backend.tenant.model;

import java.time.Instant;

public record PlatformTenantSharedVectorSummary(
    int activeHandleCount,
    int historicalHandleCount,
    String latestStatus,
    String latestVectorStrategy,
    String latestScopeType,
    String latestScopePattern,
    Instant latestUpdatedAt,
    String cleanupReadinessStatus,
    String cleanupReadinessMessage,
    String latestSummary
) {
}
