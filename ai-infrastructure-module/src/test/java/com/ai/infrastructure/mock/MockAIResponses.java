package com.ai.infrastructure.mock;

import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AISearchResponse;

import java.util.*;

/**
 * Mock AI Responses for Testing
 * 
 * This class provides pre-defined mock responses for AI services
 * to enable deterministic testing without external API calls.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
public class MockAIResponses {
    
    // Mock Embeddings
    public static final List<Double> LUXURY_WATCH_EMBEDDING = Arrays.asList(
        0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0,
        0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.0
    );
    
    public static final List<Double> DESIGNER_HANDBAG_EMBEDDING = Arrays.asList(
        0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 0.9,
        0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.0, 0.1
    );
    
    public static final List<Double> LUXURY_JEWELRY_EMBEDDING = Arrays.asList(
        0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 0.9, 0.8,
        0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.0, 0.1, 0.2
    );
    
    public static final List<Double> QUERY_EMBEDDING = Arrays.asList(
        0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95, 0.95,
        0.85, 0.75, 0.65, 0.55, 0.45, 0.35, 0.25, 0.15, 0.05, 0.05
    );
    
    /**
     * Get mock embedding response for luxury watch
     */
    public static AIEmbeddingResponse luxuryWatchEmbedding() {
        return AIEmbeddingResponse.builder()
            .embedding(LUXURY_WATCH_EMBEDDING)
            .model("text-embedding-3-small")
            .dimensions(20)
            .processingTimeMs(100L)
            .requestId("mock-watch-embedding-001")
            .build();
    }
    
    /**
     * Get mock embedding response for designer handbag
     */
    public static AIEmbeddingResponse designerHandbagEmbedding() {
        return AIEmbeddingResponse.builder()
            .embedding(DESIGNER_HANDBAG_EMBEDDING)
            .model("text-embedding-3-small")
            .dimensions(20)
            .processingTimeMs(120L)
            .requestId("mock-handbag-embedding-002")
            .build();
    }
    
    /**
     * Get mock embedding response for luxury jewelry
     */
    public static AIEmbeddingResponse luxuryJewelryEmbedding() {
        return AIEmbeddingResponse.builder()
            .embedding(LUXURY_JEWELRY_EMBEDDING)
            .model("text-embedding-3-small")
            .dimensions(20)
            .processingTimeMs(110L)
            .requestId("mock-jewelry-embedding-003")
            .build();
    }
    
    /**
     * Get mock embedding response for search query
     */
    public static AIEmbeddingResponse queryEmbedding() {
        return AIEmbeddingResponse.builder()
            .embedding(QUERY_EMBEDDING)
            .model("text-embedding-3-small")
            .dimensions(20)
            .processingTimeMs(90L)
            .requestId("mock-query-embedding-004")
            .build();
    }
    
    /**
     * Get mock embedding response for generic content
     */
    public static AIEmbeddingResponse genericEmbedding(String content) {
        // Generate deterministic embedding based on content hash
        List<Double> embedding = generateDeterministicEmbedding(content);
        return AIEmbeddingResponse.builder()
            .embedding(embedding)
            .model("text-embedding-3-small")
            .dimensions(20)
            .processingTimeMs(95L)
            .requestId("mock-generic-embedding-" + content.hashCode())
            .build();
    }
    
    /**
     * Get mock generation response for product description
     */
    public static AIGenerationResponse productDescriptionGeneration() {
        return AIGenerationResponse.builder()
            .content("Exquisite luxury timepiece featuring a handcrafted gold case, " +
                    "Swiss movement, and genuine diamond bezel. This masterpiece represents " +
                    "the pinnacle of horological artistry and sophisticated elegance.")
            .model("gpt-4o-mini")
            .maxTokens(200)
            .temperature(0.3)
            .processingTimeMs(1500L)
            .requestId("mock-description-generation-001")
            .build();
    }
    
    /**
     * Get mock generation response for product categorization
     */
    public static AIGenerationResponse productCategorization() {
        return AIGenerationResponse.builder()
            .content("luxury,premium,exclusive,limited-edition")
            .model("gpt-4o-mini")
            .maxTokens(100)
            .temperature(0.1)
            .processingTimeMs(800L)
            .requestId("mock-categorization-001")
            .build();
    }
    
    /**
     * Get mock generation response for product tagging
     */
    public static AIGenerationResponse productTagging() {
        return AIGenerationResponse.builder()
            .content("gold,swiss-movement,diamond,bezel,luxury,premium,exclusive")
            .model("gpt-4o-mini")
            .maxTokens(150)
            .temperature(0.2)
            .processingTimeMs(700L)
            .requestId("mock-tagging-001")
            .build();
    }
    
    /**
     * Get mock search response for luxury products
     */
    public static AISearchResponse luxuryProductsSearch() {
        List<Map<String, Object>> results = Arrays.asList(
            Map.of(
                "id", "product-1",
                "content", "Luxury Rolex watch with diamond bezel",
                "score", 0.95,
                "similarity", 0.95,
                "entityType", "product",
                "metadata", "{\"category\":\"watches\",\"brand\":\"Rolex\",\"price\":5000}"
            ),
            Map.of(
                "id", "product-2", 
                "content", "Designer Chanel handbag with gold hardware",
                "score", 0.87,
                "similarity", 0.87,
                "entityType", "product",
                "metadata", "{\"category\":\"handbags\",\"brand\":\"Chanel\",\"price\":3000}"
            ),
            Map.of(
                "id", "product-3",
                "content", "Luxury diamond necklace with platinum setting",
                "score", 0.82,
                "similarity", 0.82,
                "entityType", "product", 
                "metadata", "{\"category\":\"jewelry\",\"brand\":\"Tiffany\",\"price\":8000}"
            )
        );
        
        return AISearchResponse.builder()
            .results(results)
            .totalResults(3)
            .maxScore(0.95)
            .processingTimeMs(200L)
            .requestId("mock-search-001")
            .query("luxury watch")
            .model("text-embedding-3-small")
            .build();
    }
    
    /**
     * Get mock search response with no results
     */
    public static AISearchResponse emptySearchResponse() {
        return AISearchResponse.builder()
            .results(new ArrayList<>())
            .totalResults(0)
            .maxScore(0.0)
            .processingTimeMs(50L)
            .requestId("mock-empty-search-001")
            .query("nonexistent product")
            .model("text-embedding-3-small")
            .build();
    }
    
    /**
     * Generate deterministic embedding based on content
     */
    private static List<Double> generateDeterministicEmbedding(String content) {
        List<Double> embedding = new ArrayList<>();
        int hash = content.hashCode();
        
        for (int i = 0; i < 20; i++) {
            // Generate deterministic values based on content hash and position
            double value = Math.sin(hash + i) * 0.5 + 0.5; // Normalize to 0-1
            embedding.add(value);
        }
        
        return embedding;
    }
    
    /**
     * Get mock responses for different content types
     */
    public static class ByContentType {
        
        public static AIEmbeddingResponse embedding(String content) {
            if (content.toLowerCase().contains("watch")) {
                return luxuryWatchEmbedding();
            } else if (content.toLowerCase().contains("handbag") || content.toLowerCase().contains("bag")) {
                return designerHandbagEmbedding();
            } else if (content.toLowerCase().contains("jewelry") || content.toLowerCase().contains("necklace")) {
                return luxuryJewelryEmbedding();
            } else {
                return genericEmbedding(content);
            }
        }
        
        public static AIGenerationResponse generation(String purpose) {
            switch (purpose.toLowerCase()) {
                case "description":
                case "description_generation":
                    return productDescriptionGeneration();
                case "categorization":
                    return productCategorization();
                case "tagging":
                    return productTagging();
                default:
                    return AIGenerationResponse.builder()
                        .content("Mock AI generated content for: " + purpose)
                        .model("gpt-4o-mini")
                        .maxTokens(100)
                        .temperature(0.3)
                        .processingTimeMs(1000L)
                        .requestId("mock-generation-" + purpose.hashCode())
                        .build();
            }
        }
    }
}