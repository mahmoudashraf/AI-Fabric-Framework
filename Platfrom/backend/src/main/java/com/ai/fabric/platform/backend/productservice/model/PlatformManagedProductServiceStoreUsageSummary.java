package com.ai.fabric.platform.backend.productservice.model;

import java.time.Instant;
import java.util.List;

public record PlatformManagedProductServiceStoreUsageSummary(
    String shopDomain,
    Instant generatedAt,
    Instant lastActivityAt,
    long totalToday,
    long totalLast7Days,
    List<PlatformManagedProductServiceUsageEventSummary> todayBreakdown,
    List<PlatformManagedProductServiceUsageEventSummary> last7DayBreakdown,
    List<PlatformManagedProductServiceStoreUsageSurfaceSummary> todaySurfaceUsage,
    List<PlatformManagedProductServiceStoreUsageSurfaceSummary> last7DaySurfaceUsage,
    List<PlatformManagedProductServiceStoreUsageTopQuerySummary> topQuestionsLast7Days,
    List<PlatformManagedProductServiceStoreUsageSurfaceJourneySummary> last7DaySurfaceJourneys,
    PlatformManagedProductServiceStoreUsageRoiSummary roiSummary
) {
}
