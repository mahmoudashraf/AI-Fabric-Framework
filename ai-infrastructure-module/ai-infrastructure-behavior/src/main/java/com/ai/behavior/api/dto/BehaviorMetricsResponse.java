package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorMetrics;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class BehaviorMetricsResponse {
    UUID userId;
    LocalDate metricDate;
    int viewCount;
    int clickCount;
    int searchCount;
    int addToCartCount;
    int purchaseCount;
    int feedbackCount;
    double conversionRate;
    double totalRevenue;

    public static BehaviorMetricsResponse from(BehaviorMetrics metrics) {
        return BehaviorMetricsResponse.builder()
            .userId(metrics.getUserId())
            .metricDate(metrics.getMetricDate())
            .viewCount(metrics.getViewCount())
            .clickCount(metrics.getClickCount())
            .searchCount(metrics.getSearchCount())
            .addToCartCount(metrics.getAddToCartCount())
            .purchaseCount(metrics.getPurchaseCount())
            .feedbackCount(metrics.getFeedbackCount())
            .conversionRate(metrics.getConversionRate())
            .totalRevenue(metrics.getTotalRevenue())
            .build();
    }
}
