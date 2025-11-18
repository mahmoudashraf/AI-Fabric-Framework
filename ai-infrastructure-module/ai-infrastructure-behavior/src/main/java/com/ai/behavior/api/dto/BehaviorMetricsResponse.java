package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorMetrics;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class BehaviorMetricsResponse {

    @Schema(description = "User identifier the metrics belong to.",
        example = "2ef3fa16-1d12-4c53-9ae5-7dc59a9d24c5")
    UUID userId;

    @Schema(description = "Date the aggregates cover (UTC).", example = "2025-01-10")
    LocalDate metricDate;

    @Schema(description = "All projected metrics exposed as a flat key/value map.")
    Map<String, Double> metrics;

    @Schema(description = "Structured attributes captured during projection.")
    Map<String, Object> attributes;

    @Schema(description = "Quick neutral KPI summary (0-1 scale).")
    NeutralKpiResponse neutralKpis;

    public static BehaviorMetricsResponse from(BehaviorMetrics metrics) {
        Map<String, Double> safeMetrics = new HashMap<>(metrics.safeMetrics());
        Map<String, Object> safeAttributes = new HashMap<>(metrics.safeAttributes());
        return BehaviorMetricsResponse.builder()
            .userId(metrics.getUserId())
            .metricDate(metrics.getMetricDate())
            .metrics(safeMetrics)
            .attributes(safeAttributes)
            .neutralKpis(NeutralKpiResponse.fromMetrics(safeMetrics))
            .build();
    }
}
