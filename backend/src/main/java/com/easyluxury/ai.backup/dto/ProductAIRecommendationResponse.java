package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ProductAIRecommendationResponse
 * 
 * Response DTO for product AI recommendation operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAIRecommendationResponse {
    
    private UUID userId;
    
    private List<ProductAIRecommendationItem> recommendations;
    
    private Integer totalRecommendations;
    
    private List<String> categories;
    
    private String priceRange;
    
    private String recommendationType;
    
    private LocalDateTime generationTimestamp;
    
    private Double confidenceScore;
    
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAIRecommendationItem {
        private UUID productId;
        private String name;
        private String description;
        private Double price;
        private String category;
        private String brand;
        private String imageUrl;
        private Double recommendationScore;
        private String reason;
        private Map<String, Object> metadata;
    }
}