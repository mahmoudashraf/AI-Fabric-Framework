package com.ai.infrastructure.config;

import com.ai.infrastructure.dto.AIConfigurationDto;
import com.ai.infrastructure.service.AIConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIConfigurationServiceTest {
    
    private AIProviderConfig providerConfig;
    
    @Mock
    private AIServiceConfig serviceConfig;
    
    private AIConfigurationService configurationService;
    
    @BeforeEach
    void setUp() {
        providerConfig = new AIProviderConfig();
        providerConfig.setLlmProvider("openai");
        providerConfig.setEmbeddingProvider("onnx");
        providerConfig.getOpenai().setApiKey("test-key");
        providerConfig.getOpenai().setModel("gpt-4o-mini");
        providerConfig.getOpenai().setEmbeddingModel("text-embedding-3-small");
        providerConfig.getOpenai().setMaxTokens(2000);
        providerConfig.getOpenai().setTemperature(0.3);
        providerConfig.getOpenai().setTimeout(60);
        providerConfig.getOnnx().setModelAlias("text-embedding-3-small");
        providerConfig.getOnnx().setModelPath("classpath:/models/embeddings/all-MiniLM-L6-v2.onnx");
        providerConfig.getOnnx().setTokenizerPath("classpath:/models/embeddings/tokenizer.json");
        providerConfig.getPinecone().setApiKey("pinecone-key");
        providerConfig.getPinecone().setEnvironment("us-west1-gcp");
        providerConfig.getPinecone().setIndexName("ai-infrastructure");
        configurationService = new AIConfigurationService(providerConfig, serviceConfig);
    }
    
    @Test
    void testGetConfiguration() {
        // Given
        when(serviceConfig.isEnabled()).thenReturn(true);
        when(serviceConfig.isAutoConfiguration()).thenReturn(true);
        when(serviceConfig.isCachingEnabled()).thenReturn(true);
        when(serviceConfig.isMetricsEnabled()).thenReturn(true);
        when(serviceConfig.isHealthChecksEnabled()).thenReturn(true);
        when(serviceConfig.isLoggingEnabled()).thenReturn(true);
        when(serviceConfig.getDefaultTimeout()).thenReturn(30000L);
        when(serviceConfig.getMaxRetries()).thenReturn(3);
        when(serviceConfig.getRetryDelay()).thenReturn(1000L);
        when(serviceConfig.isAsyncEnabled()).thenReturn(true);
        when(serviceConfig.getThreadPoolSize()).thenReturn(10);
        when(serviceConfig.isBatchProcessingEnabled()).thenReturn(true);
        when(serviceConfig.getBatchSize()).thenReturn(100);
        when(serviceConfig.isRateLimitingEnabled()).thenReturn(true);
        when(serviceConfig.getRateLimitPerMinute()).thenReturn(1000);
        when(serviceConfig.isCircuitBreakerEnabled()).thenReturn(true);
        when(serviceConfig.getCircuitBreakerThreshold()).thenReturn(5);
        when(serviceConfig.getCircuitBreakerTimeout()).thenReturn(60000L);
        when(serviceConfig.isFeatureFlagsEnabled()).thenReturn(true);
        when(serviceConfig.getFeatureFlags()).thenReturn(Map.of("embedding.enabled", true));
        when(serviceConfig.getServices()).thenReturn(Map.of());
        
        // When
        AIConfigurationDto result = configurationService.getConfiguration();
        
        // Then
        assertNotNull(result);
        assertEquals("openai", result.getLlmProvider());
        assertEquals("onnx", result.getEmbeddingProvider());
        assertNotNull(result.getProviderDetails());
        assertEquals("test-key", result.getOpenaiApiKey());
        assertEquals("gpt-4o-mini", result.getOpenaiModel());
        assertEquals("text-embedding-3-small", result.getOpenaiEmbeddingModel());
        assertEquals("pinecone-key", result.getPineconeApiKey());
        assertEquals("us-west1-gcp", result.getPineconeEnvironment());
        assertEquals("ai-infrastructure", result.getPineconeIndexName());
        assertTrue(result.isEnabled());
        assertTrue(result.isAutoConfiguration());
        assertTrue(result.isCachingEnabled());
        assertTrue(result.isMetricsEnabled());
        assertTrue(result.isHealthChecksEnabled());
        assertTrue(result.isLoggingEnabled());
        assertEquals(30000L, result.getDefaultTimeout());
        assertEquals(3, result.getMaxRetries());
        assertEquals(1000L, result.getRetryDelay());
        assertTrue(result.isAsyncEnabled());
        assertEquals(10, result.getThreadPoolSize());
        assertTrue(result.isBatchProcessingEnabled());
        assertEquals(100, result.getBatchSize());
        assertTrue(result.isRateLimitingEnabled());
        assertEquals(1000, result.getRateLimitPerMinute());
        assertTrue(result.isCircuitBreakerEnabled());
        assertEquals(5, result.getCircuitBreakerThreshold());
        assertEquals(60000L, result.getCircuitBreakerTimeout());
        assertTrue(result.isFeatureFlagsEnabled());
        assertNotNull(result.getFeatureFlags());
        assertNotNull(result.getServices());
    }
    
    @Test
    void testGetProviderConfiguration() {
        // Given
        // When
        Map<String, Object> result = configurationService.getProviderConfiguration();
        
        // Then
        assertNotNull(result);
        assertEquals("openai", result.get("llmProvider"));
        assertEquals("onnx", result.get("embeddingProvider"));
        assertNotNull(result.get("providerDetails"));
        assertEquals("test-key", result.get("openaiApiKey"));
        assertEquals("gpt-4o-mini", result.get("openaiModel"));
        assertEquals("text-embedding-3-small", result.get("openaiEmbeddingModel"));
        assertEquals("pinecone-key", result.get("pineconeApiKey"));
        assertEquals("us-west1-gcp", result.get("pineconeEnvironment"));
        assertEquals("ai-infrastructure", result.get("pineconeIndexName"));
    }
    
    @Test
    void testGetServiceConfiguration() {
        // Given
        when(serviceConfig.isEnabled()).thenReturn(true);
        when(serviceConfig.isAutoConfiguration()).thenReturn(true);
        when(serviceConfig.isCachingEnabled()).thenReturn(true);
        when(serviceConfig.isMetricsEnabled()).thenReturn(true);
        when(serviceConfig.isHealthChecksEnabled()).thenReturn(true);
        when(serviceConfig.isLoggingEnabled()).thenReturn(true);
        when(serviceConfig.getDefaultTimeout()).thenReturn(30000L);
        when(serviceConfig.getMaxRetries()).thenReturn(3);
        when(serviceConfig.getRetryDelay()).thenReturn(1000L);
        when(serviceConfig.isAsyncEnabled()).thenReturn(true);
        when(serviceConfig.getThreadPoolSize()).thenReturn(10);
        when(serviceConfig.isBatchProcessingEnabled()).thenReturn(true);
        when(serviceConfig.getBatchSize()).thenReturn(100);
        when(serviceConfig.isRateLimitingEnabled()).thenReturn(true);
        when(serviceConfig.getRateLimitPerMinute()).thenReturn(1000);
        when(serviceConfig.isCircuitBreakerEnabled()).thenReturn(true);
        when(serviceConfig.getCircuitBreakerThreshold()).thenReturn(5);
        when(serviceConfig.getCircuitBreakerTimeout()).thenReturn(60000L);
        when(serviceConfig.isFeatureFlagsEnabled()).thenReturn(true);
        when(serviceConfig.getFeatureFlags()).thenReturn(Map.of("embedding.enabled", true));
        when(serviceConfig.getServices()).thenReturn(Map.of());
        
        // When
        Map<String, Object> result = configurationService.getServiceConfiguration();
        
        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("enabled"));
        assertTrue((Boolean) result.get("autoConfiguration"));
        assertTrue((Boolean) result.get("cachingEnabled"));
        assertTrue((Boolean) result.get("metricsEnabled"));
        assertTrue((Boolean) result.get("healthChecksEnabled"));
        assertTrue((Boolean) result.get("loggingEnabled"));
        assertEquals(30000L, result.get("defaultTimeout"));
        assertEquals(3, result.get("maxRetries"));
        assertEquals(1000L, result.get("retryDelay"));
        assertTrue((Boolean) result.get("asyncEnabled"));
        assertEquals(10, result.get("threadPoolSize"));
        assertTrue((Boolean) result.get("batchProcessingEnabled"));
        assertEquals(100, result.get("batchSize"));
        assertTrue((Boolean) result.get("rateLimitingEnabled"));
        assertEquals(1000, result.get("rateLimitPerMinute"));
        assertTrue((Boolean) result.get("circuitBreakerEnabled"));
        assertEquals(5, result.get("circuitBreakerThreshold"));
        assertEquals(60000L, result.get("circuitBreakerTimeout"));
        assertTrue((Boolean) result.get("featureFlagsEnabled"));
        assertNotNull(result.get("featureFlags"));
        assertNotNull(result.get("services"));
    }
    
    @Test
    void testIsFeatureEnabled() {
        // Given
        when(serviceConfig.isFeatureFlagsEnabled()).thenReturn(true);
        when(serviceConfig.getFeatureFlags()).thenReturn(Map.of("embedding.enabled", true, "search.enabled", false));
        
        // When & Then
        assertTrue(configurationService.isFeatureEnabled("embedding.enabled"));
        assertFalse(configurationService.isFeatureEnabled("search.enabled"));
        assertFalse(configurationService.isFeatureEnabled("nonexistent.feature"));
    }
    
    @Test
    void testIsFeatureEnabledWhenFeatureFlagsDisabled() {
        // Given
        when(serviceConfig.isFeatureFlagsEnabled()).thenReturn(false);
        
        // When & Then
        assertTrue(configurationService.isFeatureEnabled("any.feature"));
    }
    
    @Test
    void testValidateConfiguration() {
        // Given
        when(serviceConfig.getDefaultTimeout()).thenReturn(30000L);
        when(serviceConfig.getMaxRetries()).thenReturn(3);
        when(serviceConfig.getRetryDelay()).thenReturn(1000L);
        when(serviceConfig.getThreadPoolSize()).thenReturn(10);
        when(serviceConfig.getBatchSize()).thenReturn(100);
        when(serviceConfig.getRateLimitPerMinute()).thenReturn(1000);
        when(serviceConfig.getCircuitBreakerThreshold()).thenReturn(5);
        when(serviceConfig.getCircuitBreakerTimeout()).thenReturn(60000L);
        
        // When
        Map<String, Object> result = configurationService.validateConfiguration();
        
        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("valid"));
        assertNotNull(result.get("errors"));
    }
    
    @Test
    void testValidateConfigurationWithInvalidConfig() {
        // Given
        providerConfig.getOpenai().setApiKey("");
        when(serviceConfig.getDefaultTimeout()).thenReturn(30000L);
        when(serviceConfig.getMaxRetries()).thenReturn(3);
        when(serviceConfig.getRetryDelay()).thenReturn(1000L);
        when(serviceConfig.getThreadPoolSize()).thenReturn(10);
        when(serviceConfig.getBatchSize()).thenReturn(100);
        when(serviceConfig.getRateLimitPerMinute()).thenReturn(1000);
        when(serviceConfig.getCircuitBreakerThreshold()).thenReturn(5);
        when(serviceConfig.getCircuitBreakerTimeout()).thenReturn(60000L);
        
        // When
        Map<String, Object> result = configurationService.validateConfiguration();
        
        // Then
        assertNotNull(result);
        assertFalse((Boolean) result.get("valid"));
        assertNotNull(result.get("errors"));
        assertTrue(((Map<String, String>) result.get("errors")).containsKey("openaiApiKey"));
    }
    
    @Test
    void testGetConfigurationSummary() {
        // Given
        when(serviceConfig.isEnabled()).thenReturn(true);
        when(serviceConfig.getFeatureFlags()).thenReturn(Map.of("embedding.enabled", true, "search.enabled", false));
        when(serviceConfig.getServices()).thenReturn(Map.of());
        when(serviceConfig.isCachingEnabled()).thenReturn(true);
        when(serviceConfig.isMetricsEnabled()).thenReturn(true);
        when(serviceConfig.isHealthChecksEnabled()).thenReturn(true);
        when(serviceConfig.isAsyncEnabled()).thenReturn(true);
        when(serviceConfig.isBatchProcessingEnabled()).thenReturn(true);
        when(serviceConfig.isRateLimitingEnabled()).thenReturn(true);
        when(serviceConfig.isCircuitBreakerEnabled()).thenReturn(true);
        
        // When
        Map<String, Object> result = configurationService.getConfigurationSummary();
        
        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("enabled"));
        assertEquals(1, result.get("featuresEnabled"));
        assertEquals(2, result.get("totalFeatures"));
        assertEquals(0, result.get("servicesEnabled"));
        assertEquals(0, result.get("totalServices"));
        assertTrue((Boolean) result.get("cachingEnabled"));
        assertTrue((Boolean) result.get("metricsEnabled"));
        assertTrue((Boolean) result.get("healthChecksEnabled"));
        assertTrue((Boolean) result.get("asyncEnabled"));
        assertTrue((Boolean) result.get("batchProcessingEnabled"));
        assertTrue((Boolean) result.get("rateLimitingEnabled"));
        assertTrue((Boolean) result.get("circuitBreakerEnabled"));
    }
}
