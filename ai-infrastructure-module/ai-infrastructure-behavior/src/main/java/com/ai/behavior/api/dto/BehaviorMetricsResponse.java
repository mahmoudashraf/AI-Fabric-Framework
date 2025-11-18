package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorMetrics;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class BehaviorMetricsResponse {
    UUID userId;
    LocalDate metricDate;
    Map<String, Double> metrics;

    public static BehaviorMetricsResponse from(BehaviorMetrics metrics) {
        return BehaviorMetricsResponse.builder()
            .userId(metrics.getUserId())
            .metricDate(metrics.getMetricDate())
            .metrics(metrics.safeMetrics())
            .build();
    }
}
