package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorInsightsResponse;
import com.ai.behavior.api.dto.BehaviorMetricsResponse;
import com.ai.behavior.service.BehaviorInsightsService;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @Operation(
        summary = "Retrieve cached behavior insights for a user",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Behavior insights successfully returned",
                content = @Content(schema = @Schema(implementation = BehaviorInsightsResponse.class))
            )
        }
    )
    public ResponseEntity<BehaviorInsightsResponse> getInsights(@PathVariable UUID userId) {
        return ResponseEntity.ok(BehaviorInsightsResponse.from(insightsService.getUserInsights(userId)));
    }

    @PostMapping("/{userId}/insights/refresh")
    @Operation(
        summary = "Force a refresh of behavior insights",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Behavior insights recalculated successfully",
                content = @Content(schema = @Schema(implementation = BehaviorInsightsResponse.class))
            )
        }
    )
    public ResponseEntity<BehaviorInsightsResponse> refresh(@PathVariable UUID userId) {
        return ResponseEntity.ok(BehaviorInsightsResponse.from(insightsService.refreshInsights(userId)));
    }

    @GetMapping("/{userId}/metrics")
    @Operation(
        summary = "Retrieve recent metric projections for a user",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Metric projections returned",
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = BehaviorMetricsResponse.class)))
            )
        }
    )
    public ResponseEntity<List<BehaviorMetricsResponse>> getMetrics(@PathVariable UUID userId) {
        return ResponseEntity.ok(
            metricsRepository.findTop30ByUserIdOrderByMetricDateDesc(userId).stream()
                .map(BehaviorMetricsResponse::from)
                .toList()
        );
    }
}
