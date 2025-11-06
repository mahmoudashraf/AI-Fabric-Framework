package com.ai.infrastructure.mock;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mock AI Service for Testing
 * 
 * This service provides mock implementations of AI operations
 * for testing without external API calls. It can be enabled
 * via configuration properties.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.mock", name = "enabled", havingValue = "true")
public class MockAIService {
    
    private final AIProviderConfig config;
    
    /**
     * Mock embedding generation
     */
    public AIEmbeddingResponse generateMockEmbedding(AIEmbeddingRequest request) {
        log.debug("Generating mock embedding for: {}", request.getText());
        
        List<Double> embedding = generateDeterministicEmbedding(request.getText());
        
        return AIEmbeddingResponse.builder()
            .embedding(embedding)
            .model(config.getOpenaiEmbeddingModel())
            .dimensions(embedding.size())
            .processingTimeMs(50L) // Fast mock response
            .requestId("mock-embedding-" + UUID.randomUUID().toString().substring(0, 8))
            .build();
    }
    
    /**
     * Mock content generation
     */
    public AIGenerationResponse generateMockContent(AIGenerationRequest request) {
        log.debug("Generating mock content for purpose: {}", request.getPurpose());
        
        String content = generateMockContentByPurpose(request.getPurpose(), request.getPrompt());
        
        return AIGenerationResponse.builder()
            .content(content)
            .model(config.getOpenaiModel())
            .usage(Map.of("maxTokens", request.getMaxTokens(), "temperature", request.getTemperature()))
            .processingTimeMs(100L) // Fast mock response
            .requestId("mock-generation-" + UUID.randomUUID().toString().substring(0, 8))
            .build();
    }
    
    /**
     * Mock search response
     */
    public AISearchResponse generateMockSearch(AISearchRequest request) {
        log.debug("Generating mock search for query: {}", request.getQuery());
        
        List<Map<String, Object>> results = generateMockSearchResults(request);
        
        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .maxScore(results.isEmpty() ? 0.0 : (Double) results.get(0).get("score"))
            .processingTimeMs(75L) // Fast mock response
            .requestId("mock-search-" + UUID.randomUUID().toString().substring(0, 8))
            .query(request.getQuery())
            .model(config.getOpenaiEmbeddingModel())
            .build();
    }
    
    /**
     * Generate deterministic embedding based on content
     */
    private List<Double> generateDeterministicEmbedding(String content) {
        List<Double> embedding = new ArrayList<>();
        int hash = content.hashCode();
        
        // Generate 20-dimensional embedding
        for (int i = 0; i < 20; i++) {
            double value = Math.sin(hash + i) * 0.5 + 0.5; // Normalize to 0-1
            embedding.add(value);
        }
        
        return embedding;
    }
    
    /**
     * Generate mock content based on purpose
     */
    private String generateMockContentByPurpose(String purpose, String prompt) {
        if (purpose == null) {
            return "Mock AI generated content for: " + prompt;
        }
        
        switch (purpose.toLowerCase()) {
            case "description":
            case "description_generation":
                return "Exquisite luxury product featuring premium materials, " +
                       "handcrafted details, and sophisticated design. This masterpiece " +
                       "represents the pinnacle of quality and elegance.";
                       
            case "categorization":
                return "luxury,premium,exclusive,limited-edition";
                
            case "tagging":
                return "premium,luxury,exclusive,handcrafted,sophisticated";
                
            case "insights":
                return "Market analysis shows strong demand for luxury products. " +
                       "Recommend focusing on premium positioning and exclusive features.";
                       
            default:
                return "Mock AI generated content for " + purpose + ": " + prompt;
        }
    }
    
    /**
     * Generate mock search results
     */
    private List<Map<String, Object>> generateMockSearchResults(AISearchRequest request) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        String query = request.getQuery().toLowerCase();
        
        // Generate mock results based on query
        if (query.contains("luxury") || query.contains("premium")) {
            results.add(createMockResult("product-1", "Luxury Rolex watch with diamond bezel", 0.95));
            results.add(createMockResult("product-2", "Designer Chanel handbag with gold hardware", 0.87));
            results.add(createMockResult("product-3", "Luxury diamond necklace with platinum setting", 0.82));
        } else if (query.contains("watch") || query.contains("timepiece")) {
            results.add(createMockResult("product-1", "Luxury Rolex watch with diamond bezel", 0.92));
            results.add(createMockResult("product-4", "Swiss-made luxury timepiece with leather strap", 0.85));
        } else if (query.contains("handbag") || query.contains("bag")) {
            results.add(createMockResult("product-2", "Designer Chanel handbag with gold hardware", 0.90));
            results.add(createMockResult("product-5", "Luxury leather handbag with signature logo", 0.78));
        } else {
            // Generic results
            results.add(createMockResult("product-1", "Premium luxury product", 0.75));
            results.add(createMockResult("product-2", "Exclusive designer item", 0.70));
        }
        
        // Limit results based on request
        return results.stream()
            .limit(request.getLimit())
            .toList();
    }
    
    /**
     * Create a mock search result
     */
    private Map<String, Object> createMockResult(String id, String content, double score) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("content", content);
        result.put("score", score);
        result.put("similarity", score);
        result.put("entityType", "product");
        result.put("metadata", "{\"category\":\"luxury\",\"brand\":\"premium\"}");
        return result;
    }
}