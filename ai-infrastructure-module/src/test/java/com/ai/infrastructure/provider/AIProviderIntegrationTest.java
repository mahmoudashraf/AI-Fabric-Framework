package com.ai.infrastructure.provider;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.provider.OpenAIProvider;
import com.ai.infrastructure.provider.AnthropicProvider;
import com.ai.infrastructure.provider.CohereProvider;
import com.ai.infrastructure.provider.ProviderStatus;
import com.ai.infrastructure.provider.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Provider Integration Test
 * 
 * This test verifies that AI providers work correctly together
 * for Sequence 12 multi-provider support features.
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
    "ai.service.metrics.enabled=true"
})
public class AIProviderIntegrationTest {

    @Autowired
    private AIProviderManager providerManager;

    @Autowired
    private OpenAIProvider openAIProvider;

    @Autowired
    private AnthropicProvider anthropicProvider;

    @Autowired
    private CohereProvider cohereProvider;

    @Test
    public void testProviderManager() {
        // Test provider manager
        assertNotNull(providerManager);

        // Test provider statistics
        Map<String, Object> stats = providerManager.getProviderStatistics();
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalProviders"));
        assertTrue(stats.containsKey("availableProviders"));
        assertTrue((Integer) stats.get("totalProviders") > 0);
        assertTrue((Integer) stats.get("availableProviders") > 0);
    }

    @Test
    public void testOpenAIProvider() {
        // Test OpenAI provider
        assertNotNull(openAIProvider);
        assertEquals("OpenAI", openAIProvider.getProviderName());
        assertTrue(openAIProvider.isAvailable());

        // Test content generation
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Generate a product description for a luxury watch")
            .purpose("description")
            .maxTokens(100)
            .temperature(0.7)
            .build();

        AIGenerationResponse response = openAIProvider.generateContent(request);
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        assertTrue(response.getProcessingTimeMs() > 0);

        // Test embedding generation
        AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
            .text("Test text for embedding")
            .model("text-embedding-3-small")
            .build();

        AIEmbeddingResponse embeddingResponse = openAIProvider.generateEmbedding(embeddingRequest);
        assertNotNull(embeddingResponse);
        assertNotNull(embeddingResponse.getEmbedding());
        assertFalse(embeddingResponse.getEmbedding().isEmpty());
        assertTrue(embeddingResponse.getProcessingTimeMs() > 0);

        // Test provider status
        ProviderStatus status = openAIProvider.getStatus();
        assertNotNull(status);
        assertEquals("OpenAI", status.getProviderName());
        assertTrue(status.isAvailable());
        assertTrue(status.isHealthy());
    }

    @Test
    public void testAnthropicProvider() {
        // Test Anthropic provider
        assertNotNull(anthropicProvider);
        assertEquals("Anthropic", anthropicProvider.getProviderName());
        assertTrue(anthropicProvider.isAvailable());

        // Test content generation
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Generate a product description for a luxury watch")
            .purpose("description")
            .maxTokens(100)
            .temperature(0.7)
            .build();

        AIGenerationResponse response = anthropicProvider.generateContent(request);
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        assertTrue(response.getProcessingTimeMs() > 0);

        // Test embedding generation (should throw exception)
        AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
            .text("Test text for embedding")
            .model("text-embedding-3-small")
            .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            anthropicProvider.generateEmbedding(embeddingRequest);
        });

        // Test provider status
        ProviderStatus status = anthropicProvider.getStatus();
        assertNotNull(status);
        assertEquals("Anthropic", status.getProviderName());
        assertTrue(status.isAvailable());
        assertTrue(status.isHealthy());
    }

    @Test
    public void testCohereProvider() {
        // Test Cohere provider
        assertNotNull(cohereProvider);
        assertEquals("Cohere", cohereProvider.getProviderName());
        assertTrue(cohereProvider.isAvailable());

        // Test content generation
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Generate a product description for a luxury watch")
            .purpose("description")
            .maxTokens(100)
            .temperature(0.7)
            .build();

        AIGenerationResponse response = cohereProvider.generateContent(request);
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        assertTrue(response.getProcessingTimeMs() > 0);

        // Test embedding generation
        AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
            .text("Test text for embedding")
            .model("embed-english-v3.0")
            .build();

        AIEmbeddingResponse embeddingResponse = cohereProvider.generateEmbedding(embeddingRequest);
        assertNotNull(embeddingResponse);
        assertNotNull(embeddingResponse.getEmbedding());
        assertFalse(embeddingResponse.getEmbedding().isEmpty());
        assertTrue(embeddingResponse.getProcessingTimeMs() > 0);

        // Test provider status
        ProviderStatus status = cohereProvider.getStatus();
        assertNotNull(status);
        assertEquals("Cohere", status.getProviderName());
        assertTrue(status.isAvailable());
        assertTrue(status.isHealthy());
    }

    @Test
    public void testProviderManagerContentGeneration() {
        // Test provider manager content generation
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Generate a product description for a luxury watch")
            .purpose("description")
            .maxTokens(100)
            .temperature(0.7)
            .build();

        AIGenerationResponse response = providerManager.generateContent(request);
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        assertTrue(response.getProcessingTimeMs() > 0);
    }

    @Test
    public void testProviderManagerEmbeddingGeneration() {
        // Test provider manager embedding generation
        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text("Test text for embedding")
            .model("text-embedding-3-small")
            .build();

        AIEmbeddingResponse response = providerManager.generateEmbedding(request);
        assertNotNull(response);
        assertNotNull(response.getEmbedding());
        assertFalse(response.getEmbedding().isEmpty());
        assertTrue(response.getProcessingTimeMs() > 0);
    }

    @Test
    public void testProviderManagerFallback() {
        // Test provider manager fallback mechanism
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Test prompt for fallback")
            .purpose("test")
            .maxTokens(50)
            .temperature(0.5)
            .build();

        // This should work with any available provider
        AIGenerationResponse response = providerManager.generateContent(request);
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertTrue(response.getProcessingTimeMs() > 0);
    }

    @Test
    public void testProviderStatus() {
        // Test provider status
        List<ProviderStatus> statuses = providerManager.getAvailableProviders();
        assertNotNull(statuses);
        assertFalse(statuses.isEmpty());

        for (ProviderStatus status : statuses) {
            assertNotNull(status.getProviderName());
            assertNotNull(status.getLastSuccess());
            assertTrue(status.getTotalRequests() >= 0);
            assertTrue(status.getAverageResponseTime() >= 0);
            assertTrue(status.isAvailable());
            assertTrue(status.isHealthy());
        }
    }

    @Test
    public void testProviderConfig() {
        // Test provider configuration
        ProviderConfig openAIConfig = openAIProvider.getConfig();
        assertNotNull(openAIConfig);
        assertEquals("OpenAI", openAIConfig.getProviderName());
        assertNotNull(openAIConfig.getApiKey());
        assertNotNull(openAIConfig.getBaseUrl());
        assertNotNull(openAIConfig.getDefaultModel());

        ProviderConfig anthropicConfig = anthropicProvider.getConfig();
        assertNotNull(anthropicConfig);
        assertEquals("Anthropic", anthropicConfig.getProviderName());
        assertNotNull(anthropicConfig.getApiKey());
        assertNotNull(anthropicConfig.getBaseUrl());
        assertNotNull(anthropicConfig.getDefaultModel());

        ProviderConfig cohereConfig = cohereProvider.getConfig();
        assertNotNull(cohereConfig);
        assertEquals("Cohere", cohereConfig.getProviderName());
        assertNotNull(cohereConfig.getApiKey());
        assertNotNull(cohereConfig.getBaseUrl());
        assertNotNull(cohereConfig.getDefaultModel());
    }

    @Test
    public void testProviderHealth() {
        // Test provider health
        assertTrue(openAIProvider.isAvailable());
        assertTrue(anthropicProvider.isAvailable());
        assertTrue(cohereProvider.isAvailable());

        // Test provider status
        ProviderStatus openAIStatus = openAIProvider.getStatus();
        assertTrue(openAIStatus.isAvailable());
        assertTrue(openAIStatus.isHealthy());

        ProviderStatus anthropicStatus = anthropicProvider.getStatus();
        assertTrue(anthropicStatus.isAvailable());
        assertTrue(anthropicStatus.isHealthy());

        ProviderStatus cohereStatus = cohereProvider.getStatus();
        assertTrue(cohereStatus.isAvailable());
        assertTrue(cohereStatus.isHealthy());
    }
}
