package com.easyluxury.ai.config;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.service.AIConfigurationService;
import com.ai.infrastructure.health.AIHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@TestPropertySource(properties = {
    "easyluxury.ai.product-index-name=test-products",
    "easyluxury.ai.user-index-name=test-users",
    "easyluxury.ai.order-index-name=test-orders",
    "easyluxury.ai.enable-product-recommendations=true",
    "easyluxury.ai.enable-user-behavior-tracking=true",
    "easyluxury.ai.enable-smart-validation=true",
    "easyluxury.ai.enable-ai-content-generation=true",
    "easyluxury.ai.enable-ai-search=true",
    "easyluxury.ai.enable-ai-rag=true",
    "easyluxury.ai.default-ai-model=gpt-4o-mini",
    "easyluxury.ai.default-embedding-model=text-embedding-3-small",
    "easyluxury.ai.max-tokens=2000",
    "easyluxury.ai.temperature=0.3",
    "easyluxury.ai.timeout-seconds=60"
})
class EasyLuxuryAIConfigTest {

    @Mock
    private AIProviderConfig aiProviderConfig;

    @Mock
    private AIServiceConfig aiServiceConfig;

    @Mock
    private AIConfigurationService aiConfigurationService;

    @Mock
    private AIHealthIndicator aiHealthIndicator;

    private EasyLuxuryAIConfig easyLuxuryAIConfig;

    @BeforeEach
    void setUp() {
        easyLuxuryAIConfig = new EasyLuxuryAIConfig(
            aiProviderConfig,
            aiServiceConfig,
            aiConfigurationService,
            aiHealthIndicator
        );
    }

    @Test
    void testEasyLuxuryAISettingsBean() {
        // When
        EasyLuxuryAIConfig.EasyLuxuryAISettings settings = easyLuxuryAIConfig.easyLuxuryAISettings();

        // Then
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
        assertEquals("gpt-4o-mini", settings.getDefaultAIModel());
        assertEquals("text-embedding-3-small", settings.getDefaultEmbeddingModel());
        assertEquals(2000, settings.getMaxTokens());
        assertEquals(0.3, settings.getTemperature());
        assertEquals(60L, settings.getTimeoutSeconds());
    }

    @Test
    void testEasyLuxuryAISettingsSetters() {
        // Given
        EasyLuxuryAIConfig.EasyLuxuryAISettings settings = new EasyLuxuryAIConfig.EasyLuxuryAISettings();

        // When
        settings.setProductIndexName("custom-products");
        settings.setUserIndexName("custom-users");
        settings.setOrderIndexName("custom-orders");
        settings.setEnableProductRecommendations(false);
        settings.setEnableUserBehaviorTracking(false);
        settings.setEnableSmartValidation(false);
        settings.setEnableAIContentGeneration(false);
        settings.setEnableAISearch(false);
        settings.setEnableAIRAG(false);
        settings.setDefaultAIModel("gpt-4");
        settings.setDefaultEmbeddingModel("text-embedding-ada-002");
        settings.setMaxTokens(4000);
        settings.setTemperature(0.7);
        settings.setTimeoutSeconds(120L);

        // Then
        assertEquals("custom-products", settings.getProductIndexName());
        assertEquals("custom-users", settings.getUserIndexName());
        assertEquals("custom-orders", settings.getOrderIndexName());
        assertFalse(settings.getEnableProductRecommendations());
        assertFalse(settings.getEnableUserBehaviorTracking());
        assertFalse(settings.getEnableSmartValidation());
        assertFalse(settings.getEnableAIContentGeneration());
        assertFalse(settings.getEnableAISearch());
        assertFalse(settings.getEnableAIRAG());
        assertEquals("gpt-4", settings.getDefaultAIModel());
        assertEquals("text-embedding-ada-002", settings.getDefaultEmbeddingModel());
        assertEquals(4000, settings.getMaxTokens());
        assertEquals(0.7, settings.getTemperature());
        assertEquals(120L, settings.getTimeoutSeconds());
    }

    @Test
    void testEasyLuxuryAISettingsDefaultValues() {
        // Given
        EasyLuxuryAIConfig.EasyLuxuryAISettings settings = new EasyLuxuryAIConfig.EasyLuxuryAISettings();

        // Then
        assertEquals("easyluxury-products", settings.getProductIndexName());
        assertEquals("easyluxury-users", settings.getUserIndexName());
        assertEquals("easyluxury-orders", settings.getOrderIndexName());
        assertTrue(settings.getEnableProductRecommendations());
        assertTrue(settings.getEnableUserBehaviorTracking());
        assertTrue(settings.getEnableSmartValidation());
        assertTrue(settings.getEnableAIContentGeneration());
        assertTrue(settings.getEnableAISearch());
        assertTrue(settings.getEnableAIRAG());
        assertEquals("gpt-4o-mini", settings.getDefaultAIModel());
        assertEquals("text-embedding-3-small", settings.getDefaultEmbeddingModel());
        assertEquals(2000, settings.getMaxTokens());
        assertEquals(0.3, settings.getTemperature());
        assertEquals(60L, settings.getTimeoutSeconds());
    }
}