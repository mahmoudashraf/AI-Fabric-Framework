package com.ai.fabric.platform.backend.productservice.model;

import java.util.List;

public record PlatformManagedProductServiceUsageSummary(
    String generatedAt,
    String lastActivityAt,
    int activeShopsToday,
    int activeShopsLast7Days,
    long totalToday,
    long totalLast7Days,
    List<PlatformManagedProductServiceUsageEventSummary> todayBreakdown,
    List<PlatformManagedProductServiceUsageEventSummary> last7DayBreakdown
) {
}
