package com.ai.infrastructure.web.controller;

import com.ai.infrastructure.dto.AIHealthDto;
import com.ai.infrastructure.monitoring.AIAnalyticsService;
import com.ai.infrastructure.monitoring.AIHealthService;
import com.ai.infrastructure.monitoring.AIMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Monitoring Controller
 * 
 * REST API endpoints for AI infrastructure monitoring and analytics.
 * Provides health checks, metrics, and analytics data for observability.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/monitoring")
@RequiredArgsConstructor
public class AIMonitoringController {
    
    private final AIHealthService healthService;
    private final AIMetricsService metricsService;
    private final AIAnalyticsService analyticsService;
    
    /**
     * Get AI health status
     * 
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<AIHealthDto> getHealth() {
        log.debug("Retrieving AI health status");
        
        try {
            AIHealthDto health = healthService.getHealthStatus();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error retrieving health status", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get health summary
     * 
     * @return health summary
     */
    @GetMapping("/health/summary")
    public ResponseEntity<Map<String, Object>> getHealthSummary() {
        log.debug("Retrieving AI health summary");
        
        try {
            Map<String, Object> summary = healthService.getHealthSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error retrieving health summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Perform health check with timeout
     * 
     * @param timeoutSeconds timeout in seconds (default: 30)
     * @return health check result
     */
    @GetMapping("/health/check")
    public ResponseEntity<AIHealthDto> performHealthCheck(
            @RequestParam(defaultValue = "30") int timeoutSeconds) {
        log.debug("Performing AI health check with timeout: {} seconds", timeoutSeconds);
        
        try {
            AIHealthDto health = healthService.performHealthCheck(timeoutSeconds);
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error performing health check", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get performance metrics
     * 
     * @return performance metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        log.debug("Retrieving AI performance metrics");
        
        try {
            Map<String, Object> metrics = metricsService.getPerformanceMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error retrieving metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get provider metrics
     * 
     * @return provider metrics
     */
    @GetMapping("/metrics/providers")
    public ResponseEntity<Map<String, Object>> getProviderMetrics() {
        log.debug("Retrieving AI provider metrics");
        
        try {
            Map<String, Object> metrics = metricsService.getProviderMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error retrieving provider metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get service metrics
     * 
     * @return service metrics
     */
    @GetMapping("/metrics/services")
    public ResponseEntity<Map<String, Object>> getServiceMetrics() {
        log.debug("Retrieving AI service metrics");
        
        try {
            Map<String, Object> metrics = metricsService.getServiceMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error retrieving service metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get error metrics
     * 
     * @return error metrics
     */
    @GetMapping("/metrics/errors")
    public ResponseEntity<Map<String, Object>> getErrorMetrics() {
        log.debug("Retrieving AI error metrics");
        
        try {
            Map<String, Object> metrics = metricsService.getErrorMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error retrieving error metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get analytics report
     * 
     * @return analytics report
     */
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        log.debug("Retrieving AI analytics report");
        
        try {
            Map<String, Object> analytics = analyticsService.getAnalyticsReport();
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            log.error("Error retrieving analytics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get trends analysis
     * 
     * @return trends analysis
     */
    @GetMapping("/analytics/trends")
    public ResponseEntity<Map<String, Object>> getTrends() {
        log.debug("Retrieving AI trends analysis");
        
        try {
            Map<String, Object> analytics = analyticsService.getAnalyticsReport();
            @SuppressWarnings("unchecked")
            Map<String, Object> trends = (Map<String, Object>) analytics.get("trends");
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("Error retrieving trends", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get performance insights
     * 
     * @return performance insights
     */
    @GetMapping("/analytics/insights")
    public ResponseEntity<Map<String, Object>> getInsights() {
        log.debug("Retrieving AI performance insights");
        
        try {
            Map<String, Object> analytics = analyticsService.getAnalyticsReport();
            @SuppressWarnings("unchecked")
            Map<String, Object> insights = (Map<String, Object>) analytics.get("performanceInsights");
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            log.error("Error retrieving insights", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get recommendations
     * 
     * @return recommendations
     */
    @GetMapping("/analytics/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations() {
        log.debug("Retrieving AI recommendations");
        
        try {
            Map<String, Object> analytics = analyticsService.getAnalyticsReport();
            @SuppressWarnings("unchecked")
            Map<String, Object> recommendations = (Map<String, Object>) analytics.get("recommendations");
            return ResponseEntity.ok(Map.of("recommendations", recommendations));
        } catch (Exception e) {
            log.error("Error retrieving recommendations", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Reset metrics
     * 
     * @return success response
     */
    @PostMapping("/metrics/reset")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        log.info("Resetting AI metrics");
        
        try {
            metricsService.resetMetrics();
            return ResponseEntity.ok(Map.of("message", "Metrics reset successfully"));
        } catch (Exception e) {
            log.error("Error resetting metrics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to reset metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Check if AI services are healthy
     * 
     * @return health status
     */
    @GetMapping("/health/status")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        log.debug("Checking AI health status");
        
        try {
            boolean healthy = healthService.isHealthy();
            return ResponseEntity.ok(Map.of(
                "healthy", healthy,
                "status", healthy ? "UP" : "DOWN"
            ));
        } catch (Exception e) {
            log.error("Error checking health status", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("healthy", false, "status", "ERROR", "error", e.getMessage()));
        }
    }
}