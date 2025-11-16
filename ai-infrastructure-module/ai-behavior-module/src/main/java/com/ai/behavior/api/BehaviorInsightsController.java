package com.ai.behavior.api;

import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.repository.BehaviorMetricsRepository;
import com.ai.behavior.service.BehaviorInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-behavior/users")
@RequiredArgsConstructor
public class BehaviorInsightsController {

    private final BehaviorInsightsService insightsService;
    private final BehaviorMetricsRepository metricsRepository;

    @GetMapping("/{userId}/insights")
    public ResponseEntity<BehaviorInsights> getInsights(@PathVariable UUID userId) {
        return ResponseEntity.ok(insightsService.getUserInsights(userId));
    }

    @GetMapping("/{userId}/metrics")
    public ResponseEntity<List<BehaviorMetrics>> getMetrics(
        @PathVariable UUID userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        LocalDate effectiveEnd = end != null ? end : LocalDate.now();
        LocalDate effectiveStart = start != null ? start : effectiveEnd.minusDays(30);
        List<BehaviorMetrics> metrics = metricsRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateDesc(userId, effectiveStart, effectiveEnd);
        return ResponseEntity.ok(metrics);
    }
}
