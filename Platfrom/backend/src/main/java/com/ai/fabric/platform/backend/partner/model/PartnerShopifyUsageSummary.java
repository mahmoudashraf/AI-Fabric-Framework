package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerShopifyUsageSummary(
    String status,
    Instant generatedAt,
    Instant lastActivityAt,
    long totalToday,
    long totalLast7Days,
    List<PartnerShopifyUsageBreakdownSummary> todayBreakdown,
    List<PartnerShopifyUsageBreakdownSummary> last7DayBreakdown,
    List<PartnerShopifyUsageBreakdownSummary> surfaceUsage,
    List<PartnerShopifyUsageBreakdownSummary> topQuestions,
    List<PartnerShopifyUsageBreakdownSummary> surfaceJourneys,
    String roiStatus,
    String roiMessage,
    List<String> roiRecommendations,
    String reason
) {
}
