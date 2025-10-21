package com.easyluxury.ai.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.dto.AIHealthDto;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.ai.infrastructure.config.AIConfigurationService;
import com.easyluxury.ai.config.EasyLuxuryAIConfig.EasyLuxuryAISettings;
import com.easyluxury.ai.dto.AIHealthStatusDto;
import com.easyluxury.ai.dto.AIConfigurationStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIHealthServiceTest {

    @Mock
    private AIHealthIndicator aiHealthIndicator;

    @Mock
    private AIConfigurationService aiConfigurationService;

    @Mock
    private EasyLuxuryAISettings aiSettings;

    @Mock
    private AIProviderConfig aiProviderConfig;

    @Mock
    private AIServiceConfig aiServiceConfig;

    private AIHealthService aiHealthService;

    @BeforeEach
    void setUp() {
        aiHealthService = new AIHealthService(
            aiHealthIndicator,
            aiConfigurationService,
            aiSettings,
            aiProviderConfig,
            aiServiceConfig
        );
    }

    @Test
    void testGetAIHealthStatus() {
        // Given
        Map<String, Object> healthMap = Map.of(
            "status", "UP",
            "healthy", true,
            "message", "AI services are healthy"
        );
        
        AIHealthDto detailedHealth = AIHealthDto.builder()
            .enabled(true)
            .status("UP")
            .configurationValid(true)
            .providerConfigurationValid(true)
            .serviceConfigurationValid(true)
            .featuresEnabled(6)
            .totalFeatures(6)
            .servicesEnabled(6)
            .totalServices(6)
            .cachingEnabled(true)
            .metricsEnabled(true)
            .healthChecksEnabled(true)
            .asyncEnabled(true)
            .batchProcessingEnabled(true)
            .rateLimitingEnabled(true)
            .circuitBreakerEnabled(true)
            .openaiConfigured(true)
            .pineconeConfigured(false)
            .build();
        
        when(aiHealthIndicator.health()).thenReturn(healthMap);
        when(aiHealthIndicator.getDetailedHealth()).thenReturn(Map.of("health", detailedHealth));
        when(aiSettings.getEnableProductRecommendations()).thenReturn(true);
        when(aiSettings.getEnableUserBehaviorTracking()).thenReturn(true);
        when(aiSettings.getEnableSmartValidation()).thenReturn(true);
        when(aiSettings.getEnableAIContentGeneration()).thenReturn(true);
        when(aiSettings.getEnableAISearch()).thenReturn(true);
        when(aiSettings.getEnableAIRAG()).thenReturn(true);

        // When
        AIHealthStatusDto status = aiHealthService.getAIHealthStatus();

        // Then
        assertNotNull(status);
        assertEquals("UP", status.getOverallStatus());
        assertTrue(status.getHealthy());
        assertEquals("AI services are healthy", status.getMessage());
        assertNotNull(status.getTimestamp());
        assertTrue(status.getProductRecommendationsEnabled());
        assertTrue(status.getUserBehaviorTrackingEnabled());
        assertTrue(status.getSmartValidationEnabled());
        assertTrue(status.getAiContentGenerationEnabled());
        assertTrue(status.getAiSearchEnabled());
        assertTrue(status.getAiRAGEnabled());
        assertNotNull(status.getConfigurationStatus());
        assertNotNull(status.getPerformanceMetrics());
    }

    @Test
    void testGetAIHealthStatusWithError() {
        // Given
        when(aiHealthIndicator.health()).thenThrow(new RuntimeException("Health check failed"));

        // When
        AIHealthStatusDto status = aiHealthService.getAIHealthStatus();

        // Then
        assertNotNull(status);
        assertEquals("ERROR", status.getOverallStatus());
        assertFalse(status.getHealthy());
        assertTrue(status.getMessage().contains("Error retrieving AI health status"));
        assertNotNull(status.getTimestamp());
        assertEquals("RuntimeException", status.getError());
    }

    @Test
    void testGetConfigurationStatus() {
        // Given
        when(aiProviderConfig.getOpenaiApiKey()).thenReturn("test-key");
        when(aiProviderConfig.getPineconeApiKey()).thenReturn(null);
        when(aiServiceConfig.isEnabled()).thenReturn(true);
        when(aiServiceConfig.isCachingEnabled()).thenReturn(true);
        when(aiServiceConfig.isMetricsEnabled()).thenReturn(true);
        when(aiServiceConfig.isHealthChecksEnabled()).thenReturn(true);
        when(aiServiceConfig.isAsyncEnabled()).thenReturn(true);
        when(aiServiceConfig.isBatchProcessingEnabled()).thenReturn(true);
        when(aiServiceConfig.isRateLimitingEnabled()).thenReturn(true);
        when(aiServiceConfig.isCircuitBreakerEnabled()).thenReturn(true);
        when(aiSettings.getEnableProductRecommendations()).thenReturn(true);
        when(aiSettings.getEnableUserBehaviorTracking()).thenReturn(true);
        when(aiSettings.getEnableSmartValidation()).thenReturn(true);
        when(aiSettings.getEnableAIContentGeneration()).thenReturn(true);
        when(aiSettings.getEnableAISearch()).thenReturn(true);
        when(aiSettings.getEnableAIRAG()).thenReturn(true);
        when(aiSettings.getDefaultAIModel()).thenReturn("gpt-4o-mini");
        when(aiSettings.getDefaultEmbeddingModel()).thenReturn("text-embedding-3-small");
        when(aiSettings.getMaxTokens()).thenReturn(2000);
        when(aiSettings.getTemperature()).thenReturn(0.3);
        when(aiSettings.getTimeoutSeconds()).thenReturn(60L);
        when(aiSettings.getProductIndexName()).thenReturn("easyluxury-products");
        when(aiSettings.getUserIndexName()).thenReturn("easyluxury-users");
        when(aiSettings.getOrderIndexName()).thenReturn("easyluxury-orders");

        // When
        AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();

        // Then
        assertNotNull(configStatus);
        assertTrue(configStatus.getOpenaiConfigured());
        assertFalse(configStatus.getPineconeConfigured());
        assertTrue(configStatus.getAiServicesEnabled());
        assertTrue(configStatus.getCachingEnabled());
        assertTrue(configStatus.getMetricsEnabled());
        assertTrue(configStatus.getHealthChecksEnabled());
        assertTrue(configStatus.getAsyncEnabled());
        assertTrue(configStatus.getBatchProcessingEnabled());
        assertTrue(configStatus.getRateLimitingEnabled());
        assertTrue(configStatus.getCircuitBreakerEnabled());
        assertTrue(configStatus.getProductRecommendationsEnabled());
        assertTrue(configStatus.getUserBehaviorTrackingEnabled());
        assertTrue(configStatus.getSmartValidationEnabled());
        assertTrue(configStatus.getAiContentGenerationEnabled());
        assertTrue(configStatus.getAiSearchEnabled());
        assertTrue(configStatus.getAiRAGEnabled());
        assertEquals("gpt-4o-mini", configStatus.getDefaultAIModel());
        assertEquals("text-embedding-3-small", configStatus.getDefaultEmbeddingModel());
        assertEquals(2000, configStatus.getMaxTokens());
        assertEquals(0.3, configStatus.getTemperature());
        assertEquals(60L, configStatus.getTimeoutSeconds());
        assertEquals("easyluxury-products", configStatus.getProductIndexName());
        assertEquals("easyluxury-users", configStatus.getUserIndexName());
        assertEquals("easyluxury-orders", configStatus.getOrderIndexName());
    }

    @Test
    void testGetPerformanceMetrics() {
        // Given
        when(aiServiceConfig.getDefaultTimeout()).thenReturn(30000);
        when(aiServiceConfig.getMaxRetries()).thenReturn(3);
        when(aiServiceConfig.getRetryDelay()).thenReturn(1000L);
        when(aiServiceConfig.getThreadPoolSize()).thenReturn(10);
        when(aiServiceConfig.getBatchSize()).thenReturn(100);
        when(aiServiceConfig.getRateLimitPerMinute()).thenReturn(1000);
        when(aiServiceConfig.getCircuitBreakerThreshold()).thenReturn(5);
        when(aiServiceConfig.getCircuitBreakerTimeout()).thenReturn(60000);
        when(aiServiceConfig.getFeatureFlags()).thenReturn(Map.of("embedding.enabled", true));
        when(aiServiceConfig.getServices()).thenReturn(Map.of());

        // When
        Map<String, Object> metrics = aiHealthService.getPerformanceMetrics();

        // Then
        assertNotNull(metrics);
        assertEquals(30000L, metrics.get("defaultTimeout"));
        assertEquals(3, metrics.get("maxRetries"));
        assertEquals(1000L, metrics.get("retryDelay"));
        assertEquals(10, metrics.get("threadPoolSize"));
        assertEquals(100, metrics.get("batchSize"));
        assertEquals(1000, metrics.get("rateLimitPerMinute"));
        assertEquals(5, metrics.get("circuitBreakerThreshold"));
        assertEquals(60000L, metrics.get("circuitBreakerTimeout"));
        assertNotNull(metrics.get("featureFlags"));
        assertNotNull(metrics.get("services"));
    }

    @Test
    void testValidateConfiguration() {
        // Given
        when(aiConfigurationService.validateConfiguration()).thenReturn(true);
        when(aiSettings.getMaxTokens()).thenReturn(2000);
        when(aiSettings.getTemperature()).thenReturn(0.5);
        when(aiSettings.getTimeoutSeconds()).thenReturn(60L);
        when(aiSettings.getProductIndexName()).thenReturn("test-products");
        when(aiSettings.getUserIndexName()).thenReturn("test-users");
        when(aiSettings.getOrderIndexName()).thenReturn("test-orders");

        // When
        Map<String, Object> validation = aiHealthService.validateConfiguration();

        // Then
        assertNotNull(validation);
        assertTrue((Boolean) validation.get("valid"));
        assertNotNull(validation.get("errors"));
        
        verify(aiConfigurationService).validateConfiguration();
    }

    @Test
    void testValidateConfigurationWithErrors() {
        // Given
        when(aiConfigurationService.validateConfiguration()).thenReturn(true);
        when(aiSettings.getMaxTokens()).thenReturn(-1);
        when(aiSettings.getTemperature()).thenReturn(3.0);
        when(aiSettings.getTimeoutSeconds()).thenReturn(-1L);
        when(aiSettings.getProductIndexName()).thenReturn("");
        when(aiSettings.getUserIndexName()).thenReturn(null);
        when(aiSettings.getOrderIndexName()).thenReturn("test-orders");

        // When
        Map<String, Object> validation = aiHealthService.validateConfiguration();

        // Then
        assertNotNull(validation);
        assertFalse((Boolean) validation.get("valid"));
        assertNotNull(validation.get("errors"));
        
        Map<String, String> errors = (Map<String, String>) validation.get("errors");
        assertTrue(errors.containsKey("maxTokens"));
        assertTrue(errors.containsKey("temperature"));
        assertTrue(errors.containsKey("timeoutSeconds"));
        assertTrue(errors.containsKey("productIndexName"));
        assertTrue(errors.containsKey("userIndexName"));
    }

    @Test
    void testGetConfigurationSummary() {
        // Given
        Map<String, Object> mockSummary = Map.of(
            "enabled", true,
            "featuresEnabled", 6,
            "totalFeatures", 6
        );
        
        when(aiConfigurationService.getConfigurationSummary()).thenReturn(mockSummary);
        when(aiSettings.getEnableProductRecommendations()).thenReturn(true);
        when(aiSettings.getEnableUserBehaviorTracking()).thenReturn(true);
        when(aiSettings.getEnableSmartValidation()).thenReturn(true);
        when(aiSettings.getEnableAIContentGeneration()).thenReturn(true);
        when(aiSettings.getEnableAISearch()).thenReturn(true);
        when(aiSettings.getEnableAIRAG()).thenReturn(true);
        when(aiSettings.getDefaultAIModel()).thenReturn("gpt-4o-mini");
        when(aiSettings.getDefaultEmbeddingModel()).thenReturn("text-embedding-3-small");
        when(aiSettings.getMaxTokens()).thenReturn(2000);
        when(aiSettings.getTemperature()).thenReturn(0.3);
        when(aiSettings.getTimeoutSeconds()).thenReturn(60L);

        // When
        Map<String, Object> summary = aiHealthService.getConfigurationSummary();

        // Then
        assertNotNull(summary);
        assertTrue((Boolean) summary.get("enabled"));
        assertEquals(6, summary.get("featuresEnabled"));
        assertEquals(6, summary.get("totalFeatures"));
        assertTrue(summary.containsKey("easyluxurySettings"));
        
        verify(aiConfigurationService).getConfigurationSummary();
    }
}