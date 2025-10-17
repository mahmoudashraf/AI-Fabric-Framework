package com.easyluxury.ai.service;

import com.easyluxury.ai.dto.AIHealthStatusDto;
import com.easyluxury.ai.dto.AIConfigurationStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AI Monitoring Service Test
 * 
 * Unit tests for AI Monitoring Service.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AIMonitoringServiceTest {
    
    @Mock
    private AIHealthService aiHealthService;
    
    @InjectMocks
    private AIMonitoringService aiMonitoringService;
    
    private AIHealthStatusDto healthStatus;
    private AIConfigurationStatusDto configStatus;
    
    @BeforeEach
    void setUp() {
        healthStatus = AIHealthStatusDto.builder()
            .overallStatus("HEALTHY")
            .coreServiceStatus("UP")
            .embeddingServiceStatus("UP")
            .searchServiceStatus("UP")
            .ragServiceStatus("UP")
            .message("All services are healthy")
            .timestamp(LocalDateTime.now())
            .build();
        
        configStatus = AIConfigurationStatusDto.builder()
            .configurationValid(true)
            .message("Configuration is valid")
            .providerSettings(Map.of("openai", Map.of("enabled", true)))
            .serviceSettings(Map.of("enabled", true))
            .easyluxurySettings(Map.of("enabled", true))
            .featureFlags(Map.of("generation", true, "search", true))
            .build();
    }
    
    // ==================== Monitoring Operations Tests ====================
    
    @Test
    void testRecordSuccessfulOperation() {
        aiMonitoringService.recordSuccessfulOperation("generation", 1000L);
        aiMonitoringService.recordSuccessfulOperation("search", 500L);
        
        Map<String, Object> metrics = aiMonitoringService.getMonitoringMetrics();
        
        assertEquals(2L, metrics.get("totalRequests"));
        assertEquals(2L, metrics.get("successfulRequests"));
        assertEquals(0L, metrics.get("failedRequests"));
        assertEquals(1.0, (Double) metrics.get("successRate"));
        assertEquals(750.0, (Double) metrics.get("averageProcessingTime"));
    }
    
    @Test
    void testRecordFailedOperation() {
        aiMonitoringService.recordFailedOperation("generation", "API error");
        aiMonitoringService.recordFailedOperation("search", "Timeout error");
        
        Map<String, Object> metrics = aiMonitoringService.getMonitoringMetrics();
        
        assertEquals(2L, metrics.get("totalRequests"));
        assertEquals(0L, metrics.get("successfulRequests"));
        assertEquals(2L, metrics.get("failedRequests"));
        assertEquals(0.0, (Double) metrics.get("successRate"));
    }
    
    @Test
    void testRecordMixedOperations() {
        aiMonitoringService.recordSuccessfulOperation("generation", 1000L);
        aiMonitoringService.recordSuccessfulOperation("search", 500L);
        aiMonitoringService.recordFailedOperation("generation", "API error");
        
        Map<String, Object> metrics = aiMonitoringService.getMonitoringMetrics();
        
        assertEquals(3L, metrics.get("totalRequests"));
        assertEquals(2L, metrics.get("successfulRequests"));
        assertEquals(1L, metrics.get("failedRequests"));
        assertEquals(2.0/3.0, (Double) metrics.get("successRate"));
    }
    
    // ==================== Metrics Tests ====================
    
    @Test
    void testGetMonitoringMetrics() {
        aiMonitoringService.recordSuccessfulOperation("generation", 1000L);
        aiMonitoringService.recordSuccessfulOperation("search", 500L);
        aiMonitoringService.recordFailedOperation("generation", "API error");
        
        Map<String, Object> metrics = aiMonitoringService.getMonitoringMetrics();
        
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("timestamp"));
        assertTrue(metrics.containsKey("totalRequests"));
        assertTrue(metrics.containsKey("successfulRequests"));
        assertTrue(metrics.containsKey("failedRequests"));
        assertTrue(metrics.containsKey("successRate"));
        assertTrue(metrics.containsKey("averageProcessingTime"));
        assertTrue(metrics.containsKey("serviceRequestCounts"));
        assertTrue(metrics.containsKey("serviceErrorCounts"));
    }
    
    @Test
    void testGetPerformanceStatistics() {
        aiMonitoringService.recordSuccessfulOperation("generation", 1000L);
        aiMonitoringService.recordSuccessfulOperation("search", 500L);
        aiMonitoringService.recordFailedOperation("generation", "API error");
        
        Map<String, Object> stats = aiMonitoringService.getPerformanceStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("timestamp"));
        assertTrue(stats.containsKey("totalRequests"));
        assertTrue(stats.containsKey("successfulRequests"));
        assertTrue(stats.containsKey("failedRequests"));
        assertTrue(stats.containsKey("successRate"));
        assertTrue(stats.containsKey("failureRate"));
        assertTrue(stats.containsKey("averageProcessingTime"));
        assertTrue(stats.containsKey("totalProcessingTime"));
    }
    
    @Test
    void testGetServiceMetrics() {
        aiMonitoringService.recordSuccessfulOperation("generation", 1000L);
        aiMonitoringService.recordSuccessfulOperation("search", 500L);
        aiMonitoringService.recordFailedOperation("generation", "API error");
        
        Map<String, Object> metrics = aiMonitoringService.getServiceMetrics();
        
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("timestamp"));
        assertTrue(metrics.containsKey("serviceRequestCounts"));
        assertTrue(metrics.containsKey("serviceErrorCounts"));
        assertTrue(metrics.containsKey("serviceErrorRates"));
    }
    
    // ==================== Health Status Tests ====================
    
    @Test
    void testGetEnhancedHealthStatus_Success() {
        when(aiHealthService.getHealthStatus()).thenReturn(healthStatus);
        
        Map<String, Object> enhancedStatus = aiMonitoringService.getEnhancedHealthStatus();
        
        assertNotNull(enhancedStatus);
        assertTrue(enhancedStatus.containsKey("healthStatus"));
        assertTrue(enhancedStatus.containsKey("monitoringMetrics"));
        assertTrue(enhancedStatus.containsKey("timestamp"));
        
        verify(aiHealthService).getHealthStatus();
    }
    
    @Test
    void testGetEnhancedHealthStatus_Error() {
        when(aiHealthService.getHealthStatus()).thenThrow(new RuntimeException("Health service error"));
        
        Map<String, Object> enhancedStatus = aiMonitoringService.getEnhancedHealthStatus();
        
        assertNotNull(enhancedStatus);
        assertTrue(enhancedStatus.containsKey("healthStatus"));
        assertTrue(enhancedStatus.containsKey("monitoringMetrics"));
        assertTrue(enhancedStatus.containsKey("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> healthStatusMap = (Map<String, Object>) enhancedStatus.get("healthStatus");
        assertEquals("ERROR", healthStatusMap.get("overallStatus"));
        assertTrue(healthStatusMap.get("message").toString().contains("Health service error"));
    }
    
    @Test
    void testGetEnhancedConfigurationStatus_Success() {
        when(aiHealthService.getConfigurationStatus()).thenReturn(configStatus);
        
        Map<String, Object> enhancedStatus = aiMonitoringService.getEnhancedConfigurationStatus();
        
        assertNotNull(enhancedStatus);
        assertTrue(enhancedStatus.containsKey("configurationStatus"));
        assertTrue(enhancedStatus.containsKey("monitoringMetrics"));
        assertTrue(enhancedStatus.containsKey("timestamp"));
        
        verify(aiHealthService).getConfigurationStatus();
    }
    
    @Test
    void testGetEnhancedConfigurationStatus_Error() {
        when(aiHealthService.getConfigurationStatus()).thenThrow(new RuntimeException("Config service error"));
        
        Map<String, Object> enhancedStatus = aiMonitoringService.getEnhancedConfigurationStatus();
        
        assertNotNull(enhancedStatus);
        assertTrue(enhancedStatus.containsKey("configurationStatus"));
        assertTrue(enhancedStatus.containsKey("monitoringMetrics"));
        assertTrue(enhancedStatus.containsKey("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> configStatusMap = (Map<String, Object>) enhancedStatus.get("configurationStatus");
        assertEquals(false, configStatusMap.get("configurationValid"));
        assertTrue(configStatusMap.get("message").toString().contains("Config service error"));
    }
    
    // ==================== Service Health Check Tests ====================
    
    @Test
    void testGetServiceHealthCheck_Healthy() {
        when(aiHealthService.getHealthStatus()).thenReturn(healthStatus);
        
        Map<String, Object> healthCheck = aiMonitoringService.getServiceHealthCheck();
        
        assertNotNull(healthCheck);
        assertEquals("AIMonitoringService", healthCheck.get("service"));
        assertEquals("HEALTHY", healthCheck.get("status"));
        assertTrue(healthCheck.containsKey("timestamp"));
        assertTrue(healthCheck.containsKey("healthStatus"));
        assertTrue(healthCheck.containsKey("monitoringMetrics"));
        
        verify(aiHealthService).getHealthStatus();
    }
    
    @Test
    void testGetServiceHealthCheck_Unhealthy() {
        AIHealthStatusDto unhealthyStatus = AIHealthStatusDto.builder()
            .overallStatus("UNHEALTHY")
            .message("Services are down")
            .build();
        
        when(aiHealthService.getHealthStatus()).thenReturn(unhealthyStatus);
        
        Map<String, Object> healthCheck = aiMonitoringService.getServiceHealthCheck();
        
        assertNotNull(healthCheck);
        assertEquals("AIMonitoringService", healthCheck.get("service"));
        assertEquals("UNHEALTHY", healthCheck.get("status"));
        assertTrue(healthCheck.containsKey("timestamp"));
    }
    
    @Test
    void testGetServiceHealthCheck_Error() {
        when(aiHealthService.getHealthStatus()).thenThrow(new RuntimeException("Health check error"));
        
        Map<String, Object> healthCheck = aiMonitoringService.getServiceHealthCheck();
        
        assertNotNull(healthCheck);
        assertEquals("AIMonitoringService", healthCheck.get("service"));
        assertEquals("UNHEALTHY", healthCheck.get("status"));
        assertTrue(healthCheck.containsKey("timestamp"));
        assertTrue(healthCheck.containsKey("error"));
    }
    
    // ==================== Reset Tests ====================
    
    @Test
    void testResetMetrics() {
        // Record some operations
        aiMonitoringService.recordSuccessfulOperation("generation", 1000L);
        aiMonitoringService.recordFailedOperation("search", "API error");
        
        // Verify metrics exist
        Map<String, Object> metricsBefore = aiMonitoringService.getMonitoringMetrics();
        assertEquals(2L, metricsBefore.get("totalRequests"));
        
        // Reset metrics
        aiMonitoringService.resetMetrics();
        
        // Verify metrics are reset
        Map<String, Object> metricsAfter = aiMonitoringService.getMonitoringMetrics();
        assertEquals(0L, metricsAfter.get("totalRequests"));
        assertEquals(0L, metricsAfter.get("successfulRequests"));
        assertEquals(0L, metricsAfter.get("failedRequests"));
    }
    
    // ==================== Utility Tests ====================
    
    @Test
    void testGetServiceStatistics() {
        Map<String, Object> stats = aiMonitoringService.getServiceStatistics();
        
        assertNotNull(stats);
        assertEquals("AIMonitoringService", stats.get("serviceName"));
        assertEquals("1.0.0", stats.get("version"));
        assertEquals("ACTIVE", stats.get("status"));
        assertTrue(stats.containsKey("timestamp"));
        assertTrue(stats.containsKey("totalRequests"));
        assertTrue(stats.containsKey("successfulRequests"));
        assertTrue(stats.containsKey("failedRequests"));
    }
}