package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorInsightsResponse;
import com.ai.behavior.api.dto.BehaviorMetricsResponse;
import com.ai.behavior.service.BehaviorInsightsService;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-behavior/users")
public class BehaviorInsightsController {

    private final BehaviorInsightsService insightsService;
    private final BehaviorMetricsRepository metricsRepository;

    public BehaviorInsightsController(BehaviorInsightsService insightsService,
                                      BehaviorMetricsRepository metricsRepository) {
        this.insightsService = insightsService;
        this.metricsRepository = metricsRepository;
    }

    @GetMapping("/{userId}/insights")
    public ResponseEntity<BehaviorInsightsResponse> getInsights(@PathVariable UUID userId) {
        return ResponseEntity.ok(BehaviorInsightsResponse.from(insightsService.getUserInsights(userId)));
    }

    @PostMapping("/{userId}/insights/refresh")
    public ResponseEntity<BehaviorInsightsResponse> refresh(@PathVariable UUID userId) {
        return ResponseEntity.ok(BehaviorInsightsResponse.from(insightsService.refreshInsights(userId)));
    }

    @GetMapping("/{userId}/metrics")
    public ResponseEntity<List<BehaviorMetricsResponse>> getMetrics(@PathVariable UUID userId) {
        return ResponseEntity.ok(
            metricsRepository.findTop30ByUserIdOrderByMetricDateDesc(userId).stream()
                .map(BehaviorMetricsResponse::from)
                .toList()
        );
    }
}
