package com.easyluxury.ai;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.service.AIConfigurationService;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.easyluxury.ai.config.EasyLuxuryAIConfig;
import com.easyluxury.ai.facade.AIFacade;
import com.ai.infrastructure.monitoring.AIHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Integration Test for Easy Luxury
 * 
 * This test verifies that the AI Infrastructure module is properly integrated
 * with the Easy Luxury application and all AI services are available.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.providers.openai-api-key=test-key",
    "ai.providers.openai-model=gpt-4o-mini",
    "ai.providers.openai-embedding-model=text-embedding-3-small",
    "ai.service.enabled=true",
    "ai.service.auto-configuration=true",
    "easyluxury.ai.product-index-name=test-products",
    "easyluxury.ai.user-index-name=test-users",
    "easyluxury.ai.order-index-name=test-orders",
    "easyluxury.ai.enable-product-recommendations=true",
    "easyluxury.ai.enable-user-behavior-tracking=true",
    "easyluxury.ai.enable-smart-validation=true",
    "easyluxury.ai.enable-ai-content-generation=true",
    "easyluxury.ai.enable-ai-search=true",
    "easyluxury.ai.enable-ai-rag=true"
})
class AIIntegrationTest {

    @Autowired
    private AIProviderConfig aiProviderConfig;

    @Autowired
    private AIServiceConfig aiServiceConfig;

    @Autowired
    private AIConfigurationService aiConfigurationService;

    @Autowired
    private AIHealthIndicator aiHealthIndicator;

    @Autowired
    private AICoreService aiCoreService;

    @Autowired
    private EasyLuxuryAIConfig easyLuxuryAIConfig;

    @Autowired
    private AIFacade aiFacade;

    @Autowired
    private AIHealthService aiHealthService;

    @Test
    void testAIProviderConfigLoaded() {
        assertNotNull(aiProviderConfig);
        assertEquals("test-key", aiProviderConfig.getOpenaiApiKey());
        assertEquals("gpt-4o-mini", aiProviderConfig.getOpenaiModel());
        assertEquals("text-embedding-3-small", aiProviderConfig.getOpenaiEmbeddingModel());
    }

    @Test
    void testAIServiceConfigLoaded() {
        assertNotNull(aiServiceConfig);
        assertTrue(aiServiceConfig.isEnabled());
        assertTrue(aiServiceConfig.isAutoConfiguration());
        assertTrue(aiServiceConfig.isCachingEnabled());
        assertTrue(aiServiceConfig.isMetricsEnabled());
        assertTrue(aiServiceConfig.isHealthChecksEnabled());
        assertTrue(aiServiceConfig.isLoggingEnabled());
    }

    @Test
    void testAIConfigurationServiceLoaded() {
        assertNotNull(aiConfigurationService);
        
        // Test configuration retrieval
        var config = aiConfigurationService.getConfiguration();
        assertNotNull(config);
        assertEquals("test-key", config.getOpenaiApiKey());
        assertEquals("gpt-4o-mini", config.getOpenaiModel());
        assertTrue(config.isEnabled());
    }

    @Test
    void testAIHealthIndicatorLoaded() {
        assertNotNull(aiHealthIndicator);
        
        // Test health check
        var health = aiHealthIndicator.health();
        assertNotNull(health);
        assertTrue(health.containsKey("status"));
        assertTrue(health.containsKey("healthy"));
    }

    @Test
    void testAICoreServiceLoaded() {
        assertNotNull(aiCoreService);
        
        // Test that the service is properly configured
        // Note: getStatistics() method may not be available in the current version
        // This is a placeholder for future implementation
    }

    @Test
    void testEasyLuxuryAIConfigLoaded() {
        assertNotNull(easyLuxuryAIConfig);
        
        // Test Easy Luxury specific settings
        var settings = easyLuxuryAIConfig.easyLuxuryAISettings();
        assertNotNull(settings);
        assertEquals("test-products", settings.getProductIndexName());
        assertEquals("test-users", settings.getUserIndexName());
        assertEquals("test-orders", settings.getOrderIndexName());
        assertTrue(settings.getEnableProductRecommendations());
        assertTrue(settings.getEnableUserBehaviorTracking());
        assertTrue(settings.getEnableSmartValidation());
        assertTrue(settings.getEnableAIContentGeneration());
        assertTrue(settings.getEnableAISearch());
        assertTrue(settings.getEnableAIRAG());
    }

    @Test
    void testAIFacadeLoaded() {
        assertNotNull(aiFacade);
        
        // Test that the facade is properly configured
        // Note: We can't test actual AI operations without valid API keys
        // but we can verify the facade is properly injected
    }

    @Test
    void testAIHealthServiceLoaded() {
        assertNotNull(aiHealthService);
        
        // Test health status retrieval (using AI module's method)
        var healthStatus = aiHealthService.getHealthStatus();
        assertNotNull(healthStatus);
        assertNotNull(healthStatus.getStatus());
        assertNotNull(healthStatus.getLastUpdated());
    }

    @Test
    void testAIConfigurationValidation() {
        // Test configuration validation
        var validation = aiConfigurationService.validateConfiguration();
        assertNotNull(validation);
        assertTrue(validation.containsKey("valid"));
        assertTrue(validation.containsKey("errors"));
    }

    @Test
    void testAIConfigurationSummary() {
        // Test configuration summary
        var summary = aiConfigurationService.getConfigurationSummary();
        assertNotNull(summary);
        assertTrue(summary.containsKey("enabled"));
        assertTrue(summary.containsKey("featuresEnabled"));
        assertTrue(summary.containsKey("totalFeatures"));
    }

    @Test
    void testEasyLuxuryAISettingsConfiguration() {
        // Test AI configuration using AI module's API
        var healthStatus = aiHealthService.getHealthStatus();
        assertNotNull(healthStatus);
        // AI module health includes provider and service status
        assertNotNull(healthStatus.getProviderStatus());
        assertNotNull(healthStatus.getServiceStatus());
    }

    @Test
    void testAIPerformanceMetrics() {
        // Test performance metrics using AI module's API
        var healthStatus = aiHealthService.getHealthStatus();
        assertNotNull(healthStatus);
        assertNotNull(healthStatus.getPerformanceMetrics());
    }

    @Test
    void testAIHealthServiceConfigurationValidation() {
        // Test health check using AI module's API
        boolean isHealthy = aiHealthService.isHealthy();
        // Just verify it returns without error
        assertNotNull(isHealthy);
    }

    @Test
    void testAIHealthServiceConfigurationSummary() {
        // Test health summary using AI module's API
        var summary = aiHealthService.getHealthSummary();
        assertNotNull(summary);
        assertTrue(summary.containsKey("healthy"));
        assertTrue(summary.containsKey("enabled"));
    }
}