package com.ai.infrastructure.monitoring;

import com.ai.infrastructure.dto.AIHealthDto;
import com.ai.infrastructure.monitoring.AIHealthService;
import com.ai.infrastructure.monitoring.AIMetricsService;
import com.ai.infrastructure.monitoring.AIAnalyticsService;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.provider.ProviderStatus;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Monitoring Integration Test
 * 
 * This test verifies that AI monitoring, metrics, and analytics services
 * work correctly together for Sequence 12 features.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.provider.openai.mock-responses=true",
    "ai.provider.anthropic.mock-responses=true",
    "ai.provider.cohere.mock-responses=true",
    "ai.service.metrics.enabled=true",
    "ai.service.health-checks.enabled=true"
})
public class AIMonitoringIntegrationTest {

    @Autowired
    private AIHealthService healthService;

    @Autowired
    private AIMetricsService metricsService;

    @Autowired
    private AIAnalyticsService analyticsService;

    @Autowired
    private AIProviderManager providerManager;

    @Test
    public void testHealthService() {
        // Test health service
        AIHealthDto health = healthService.getHealthStatus();

        assertNotNull(health);
        assertNotNull(health.getStatus());
        assertNotNull(health.getLastUpdated());
        assertTrue(health.getProcessingTimeMs() > 0);
        assertNotNull(health.getPerformanceMetrics());
        assertNotNull(health.getProviderStatus());
        assertNotNull(health.getServiceStatus());
        assertNotNull(health.getSystemStatus());
    }

    @Test
    public void testMetricsService() {
        // Test metrics collection
        Map<String, Object> performanceMetrics = metricsService.getPerformanceMetrics();
        assertNotNull(performanceMetrics);
        assertTrue(performanceMetrics.containsKey("totalRequests"));
        assertTrue(performanceMetrics.containsKey("successfulRequests"));
        assertTrue(performanceMetrics.containsKey("averageResponseTime"));

        // Test provider metrics
        Map<String, Object> providerMetrics = metricsService.getProviderMetrics();
        assertNotNull(providerMetrics);

        // Test service metrics
        Map<String, Object> serviceMetrics = metricsService.getServiceMetrics();
        assertNotNull(serviceMetrics);

        // Test error metrics
        Map<String, Object> errorMetrics = metricsService.getErrorMetrics();
        assertNotNull(errorMetrics);
    }

    @Test
    public void testAnalyticsService() {
        // Test analytics service
        Map<String, Object> analytics = analyticsService.getAnalytics();
        assertNotNull(analytics);
        assertTrue(analytics.containsKey("usageStats"));
        assertTrue(analytics.containsKey("performanceTrends"));
        assertTrue(analytics.containsKey("recommendations"));

        // Test usage trends
        Map<String, Object> usageTrends = analyticsService.getUsageTrends();
        assertNotNull(usageTrends);

        // Test performance trends
        Map<String, Object> performanceTrends = analyticsService.getPerformanceTrends();
        assertNotNull(performanceTrends);

        // Test recommendations
        Map<String, Object> recommendations = analyticsService.getRecommendations();
        assertNotNull(recommendations);
    }

    @Test
    public void testProviderManager() {
        // Test provider manager
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Test prompt for provider manager")
            .purpose("test")
            .maxTokens(50)
            .temperature(0.5)
            .build();

        AIGenerationResponse response = providerManager.generateContent(request);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertTrue(response.getProcessingTimeMs() > 0);

        // Test provider statistics
        Map<String, Object> providerStats = providerManager.getProviderStatistics();
        assertNotNull(providerStats);
        assertTrue(providerStats.containsKey("totalProviders"));
        assertTrue(providerStats.containsKey("availableProviders"));
    }

    @Test
    public void testProviderStatus() {
        // Test provider status
        List<ProviderStatus> providerStatuses = providerManager.getAvailableProviders();
        assertNotNull(providerStatuses);
        assertFalse(providerStatuses.isEmpty());

        for (ProviderStatus status : providerStatuses) {
            assertNotNull(status.getProviderName());
            assertNotNull(status.getLastSuccess());
            assertTrue(status.getTotalRequests() >= 0);
            assertTrue(status.getAverageResponseTime() >= 0);
        }
    }

    @Test
    public void testMetricsRecording() {
        // Record some metrics
        metricsService.recordSuccess("test-service", "test-provider", 100L);
        metricsService.recordError("test-service", "test-provider", "test-error", 200L);

        // Verify metrics were recorded
        Map<String, Object> performanceMetrics = metricsService.getPerformanceMetrics();
        assertTrue((Long) performanceMetrics.get("totalRequests") > 0);
        assertTrue((Long) performanceMetrics.get("successfulRequests") > 0);
        assertTrue((Long) performanceMetrics.get("errorRequests") > 0);
    }

    @Test
    public void testHealthCheckIntegration() {
        // Test health check integration
        AIHealthDto health = healthService.getHealthStatus();

        // Verify health status includes all required components
        assertNotNull(health.getStatus());
        assertNotNull(health.getPerformanceMetrics());
        assertNotNull(health.getProviderStatus());
        assertNotNull(health.getServiceStatus());
        assertNotNull(health.getSystemStatus());
        assertNotNull(health.getLastUpdated());

        // Verify performance metrics are populated
        Map<String, Object> performanceMetrics = health.getPerformanceMetrics();
        assertTrue(performanceMetrics.containsKey("totalRequests"));
        assertTrue(performanceMetrics.containsKey("successfulRequests"));
        assertTrue(performanceMetrics.containsKey("averageResponseTime"));

        // Verify provider status is populated
        Map<String, Object> providerStatus = health.getProviderStatus();
        assertNotNull(providerStatus);

        // Verify service status is populated
        Map<String, Object> serviceStatus = health.getServiceStatus();
        assertNotNull(serviceStatus);

        // Verify system status is populated
        Map<String, Object> systemStatus = health.getSystemStatus();
        assertNotNull(systemStatus);
    }

    @Test
    public void testAnalyticsIntegration() {
        // Test analytics integration
        Map<String, Object> analytics = analyticsService.getAnalytics();

        // Verify analytics includes all required components
        assertTrue(analytics.containsKey("usageStats"));
        assertTrue(analytics.containsKey("performanceTrends"));
        assertTrue(analytics.containsKey("recommendations"));

        // Verify usage stats
        Map<String, Object> usageStats = (Map<String, Object>) analytics.get("usageStats");
        assertNotNull(usageStats);
        assertTrue(usageStats.containsKey("totalRequests"));
        assertTrue(usageStats.containsKey("requestsByService"));
        assertTrue(usageStats.containsKey("requestsByProvider"));

        // Verify performance trends
        Map<String, Object> performanceTrends = (Map<String, Object>) analytics.get("performanceTrends");
        assertNotNull(performanceTrends);
        assertTrue(performanceTrends.containsKey("responseTimeTrends"));
        assertTrue(performanceTrends.containsKey("throughputTrends"));

        // Verify recommendations
        Map<String, Object> recommendations = (Map<String, Object>) analytics.get("recommendations");
        assertNotNull(recommendations);
        assertTrue(recommendations.containsKey("performanceOptimizations"));
        assertTrue(recommendations.containsKey("resourceRecommendations"));
    }
}
