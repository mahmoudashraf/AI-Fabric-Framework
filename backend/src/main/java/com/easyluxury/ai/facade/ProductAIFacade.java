package com.easyluxury.ai.facade;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.easyluxury.ai.dto.ProductAISearchRequest;
import com.easyluxury.ai.dto.ProductAISearchResponse;
import com.easyluxury.ai.dto.ProductAIRecommendationRequest;
import com.easyluxury.ai.dto.ProductAIRecommendationResponse;
import com.easyluxury.ai.dto.ProductAIGenerationRequest;
import com.easyluxury.ai.dto.ProductAIGenerationResponse;
import com.easyluxury.ai.mapper.ProductAIMapper;
import com.easyluxury.ai.service.ProductAIService;
import com.easyluxury.entity.Product;
import com.easyluxury.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ProductAIFacade
 * 
 * Facade for product-specific AI operations providing a high-level interface
 * for AI-powered product features including search, recommendations, and content generation.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAIFacade {
    
    private final ProductAIService productAIService;
    private final ProductRepository productRepository;
    private final ProductAIMapper productAIMapper;
    
    /**
     * Search products using AI-powered semantic search
     * 
     * @param request the search request
     * @return AI-powered search results
     */
    @Transactional(readOnly = true)
    public ProductAISearchResponse searchProducts(ProductAISearchRequest request) {
        try {
            log.debug("Searching products with AI for query: {}", request.getQuery());
            
            // Convert to AI search request
            AISearchRequest aiRequest = AISearchRequest.builder()
                .query(request.getQuery())
                .entityType("product")
                .limit(request.getLimit())
                .threshold(request.getThreshold())
                .build();
            
            // Perform AI search
            AISearchResponse aiResponse = productAIService.searchProducts(aiRequest);
            
            // Convert to product search response
            ProductAISearchResponse response = ProductAISearchResponse.builder()
                .query(request.getQuery())
                .results(aiResponse.getResults())
                .totalResults(aiResponse.getTotalResults())
                .maxScore(aiResponse.getMaxScore())
                .processingTimeMs(aiResponse.getProcessingTimeMs())
                .requestId(aiResponse.getRequestId())
                .model(aiResponse.getModel())
                .searchTimestamp(java.time.LocalDateTime.now())
                .build();
            
            log.debug("Found {} products matching query: {}", response.getTotalResults(), request.getQuery());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error searching products with AI for query: {}", request.getQuery(), e);
            throw new RuntimeException("Failed to search products", e);
        }
    }
    
    /**
     * Generate product recommendations based on user preferences and behavior
     * 
     * @param request the recommendation request
     * @return AI-generated product recommendations
     */
    @Transactional(readOnly = true)
    public ProductAIRecommendationResponse generateRecommendations(ProductAIRecommendationRequest request) {
        try {
            log.debug("Generating product recommendations for user: {}", request.getUserId());
            
            // Generate AI recommendations
            String recommendations = productAIService.generateRecommendations(
                request.getUserId(),
                request.getLimit()
            );
            
            // For now, return empty list - in real implementation, this would parse the recommendations
            List<Product> recommendedProducts = List.of();
            
            // Convert to recommendation response
            ProductAIRecommendationResponse response = ProductAIRecommendationResponse.builder()
                .userId(request.getUserId())
                .recommendations(productAIMapper.toProductAIRecommendationItemList(recommendedProducts))
                .totalRecommendations(recommendedProducts.size())
                .categories(request.getCategories())
                .priceRange(request.getPriceRange())
                .generationTimestamp(java.time.LocalDateTime.now())
                .build();
            
            log.debug("Generated {} product recommendations for user: {}", 
                response.getTotalRecommendations(), request.getUserId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error generating product recommendations for user: {}", request.getUserId(), e);
            throw new RuntimeException("Failed to generate product recommendations", e);
        }
    }
    
    /**
     * Generate AI content for products (descriptions, tags, categories)
     * 
     * @param request the content generation request
     * @return AI-generated content
     */
    @Transactional
    public ProductAIGenerationResponse generateProductContent(ProductAIGenerationRequest request) {
        try {
            log.debug("Generating AI content for product: {}", request.getProductId());
            
            Product product = productRepository.findById(request.getProductId().toString())
                .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));
            
            // Generate AI content based on type
            String generatedContent = switch (request.getContentType()) {
                case "DESCRIPTION" -> productAIService.generateDescription(product);
                case "TAGS" -> productAIService.generateTags(product);
                case "CATEGORIES" -> productAIService.generateCategories(product);
                case "FEATURES" -> productAIService.generateInsights(product);
                case "SEO_DESCRIPTION" -> productAIService.generateDescription(product);
                default -> productAIService.generateDescription(product);
            };
            
            // Update product with generated content
            updateProductWithGeneratedContent(product, request.getContentType(), generatedContent);
            productRepository.save(product);
            
            // Convert to generation response
            ProductAIGenerationResponse response = ProductAIGenerationResponse.builder()
                .productId(request.getProductId())
                .contentType(request.getContentType())
                .generatedContent(generatedContent)
                .originalContent(getOriginalContent(product, request.getContentType()))
                .confidenceScore(0.85) // AI confidence score
                .generationTimestamp(java.time.LocalDateTime.now())
                .build();
            
            log.debug("Successfully generated {} content for product: {}", 
                request.getContentType(), request.getProductId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error generating AI content for product: {}", request.getProductId(), e);
            throw new RuntimeException("Failed to generate product content", e);
        }
    }
    
    /**
     * Analyze product performance and generate insights
     * 
     * @param productId the product ID
     * @return product performance insights
     */
    @Transactional(readOnly = true)
    public String analyzeProductPerformance(UUID productId) {
        try {
            log.debug("Analyzing product performance for product: {}", productId);
            
            Product product = productRepository.findById(productId.toString())
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
            
            // Generate performance analysis
            String insights = productAIService.generateAnalytics(product);
            
            log.debug("Successfully analyzed product performance for product: {}", productId);
            
            return insights;
            
        } catch (Exception e) {
            log.error("Error analyzing product performance for product: {}", productId, e);
            throw new RuntimeException("Failed to analyze product performance", e);
        }
    }
    
    /**
     * Get AI-powered product insights
     * 
     * @param productId the product ID
     * @return AI-generated product insights
     */
    @Transactional(readOnly = true)
    public String getProductInsights(UUID productId) {
        try {
            log.debug("Getting AI insights for product: {}", productId);
            
            Product product = productRepository.findById(productId.toString())
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
            
            // Generate AI insights
            String insights = productAIService.generateInsights(product);
            
            log.debug("Successfully generated AI insights for product: {}", productId);
            
            return insights;
            
        } catch (Exception e) {
            log.error("Error getting AI insights for product: {}", productId, e);
            throw new RuntimeException("Failed to get product insights", e);
        }
    }
    
    /**
     * Update product with generated content
     */
    private void updateProductWithGeneratedContent(Product product, String contentType, String content) {
        switch (contentType) {
            case "DESCRIPTION" -> product.setAiGeneratedDescription(content);
            case "TAGS" -> product.setAiTags(content);
            case "CATEGORIES" -> product.setAiCategories(content);
            // Add more content types as needed
        }
    }
    
    /**
     * Get original content for comparison
     */
    private String getOriginalContent(Product product, String contentType) {
        return switch (contentType) {
            case "DESCRIPTION" -> product.getDescription();
            case "TAGS" -> product.getTags() != null ? String.join(",", product.getTags()) : "";
            case "CATEGORIES" -> product.getCategory();
            case "FEATURES" -> product.getAttributes() != null ? product.getAttributes().toString() : "";
            case "SEO_DESCRIPTION" -> product.getDescription();
            default -> "";
        };
    }
}