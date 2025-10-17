package com.easyluxury.facade;

import com.easyluxury.entity.Product;
import com.easyluxury.service.ProductAIService;
import com.easyluxury.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Product AI Facade
 * 
 * Provides a business layer interface for AI-powered product operations.
 * This facade coordinates between the ProductAIService and other business
 * services to provide comprehensive AI functionality for products.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductAIFacade {
    
    private final ProductAIService productAIService;
    private final ProductRepository productRepository;
    
    /**
     * Enhance product with AI features
     * 
     * @param productId the product ID
     * @return enhanced product with AI-generated content
     */
    public Optional<Product> enhanceProduct(String productId) {
        try {
            log.debug("Enhancing product with AI features: {}", productId);
            
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                log.warn("Product not found for enhancement: {}", productId);
                return Optional.empty();
            }
            
            Product product = productOpt.get();
            
            // Generate AI description if not present
            if (product.getAiGeneratedDescription() == null) {
                productAIService.generateProductDescription(product);
            }
            
            // Generate AI categories if not present
            if (product.getAiCategories() == null) {
                productAIService.categorizeProduct(product);
            }
            
            // Generate AI tags if not present
            if (product.getAiTags() == null) {
                productAIService.generateProductTags(product);
            }
            
            // Index product for search if not indexed
            if (product.getSearchVector() == null) {
                productAIService.indexProduct(product);
            }
            
            log.debug("Successfully enhanced product: {}", productId);
            return Optional.of(product);
            
        } catch (Exception e) {
            log.error("Error enhancing product: {}", productId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Search products using AI
     * 
     * @param query the search query
     * @param limit maximum number of results
     * @return list of matching products
     */
    public List<Product> searchProducts(String query, int limit) {
        try {
            log.debug("Searching products with AI query: {}", query);
            
            List<Product> results = productAIService.searchProducts(query, limit);
            
            log.debug("AI search completed with {} results", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Error searching products with AI", e);
            return List.of();
        }
    }
    
    /**
     * Get product recommendations
     * 
     * @param productId the product ID to get recommendations for
     * @param limit maximum number of recommendations
     * @return list of recommended products
     */
    public List<Product> getProductRecommendations(String productId, int limit) {
        try {
            log.debug("Getting recommendations for product: {}", productId);
            
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                log.warn("Product not found for recommendations: {}", productId);
                return List.of();
            }
            
            List<Product> recommendations = productAIService.getProductRecommendations(
                productOpt.get(), limit
            );
            
            log.debug("Generated {} recommendations for product: {}", recommendations.size(), productId);
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error getting product recommendations: {}", productId, e);
            return List.of();
        }
    }
    
    /**
     * Get AI insights for a product
     * 
     * @param productId the product ID
     * @return AI insights
     */
    public Map<String, Object> getProductInsights(String productId) {
        try {
            log.debug("Getting AI insights for product: {}", productId);
            
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                log.warn("Product not found for insights: {}", productId);
                return Map.of("error", "Product not found");
            }
            
            Map<String, Object> insights = productAIService.getProductInsights(productOpt.get());
            
            log.debug("Generated insights for product: {}", productId);
            return insights;
            
        } catch (Exception e) {
            log.error("Error getting product insights: {}", productId, e);
            return Map.of("error", "Failed to generate insights");
        }
    }
    
    /**
     * Generate AI content for a product
     * 
     * @param productId the product ID
     * @param contentType the type of content to generate (description, categories, tags)
     * @return generated content
     */
    public Map<String, Object> generateProductContent(String productId, String contentType) {
        try {
            log.debug("Generating {} content for product: {}", contentType, productId);
            
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                log.warn("Product not found for content generation: {}", productId);
                return Map.of("error", "Product not found");
            }
            
            Product product = productOpt.get();
            Map<String, Object> result = Map.of();
            
            switch (contentType.toLowerCase()) {
                case "description":
                    String description = productAIService.generateProductDescription(product);
                    result = Map.of("description", description, "type", "description");
                    break;
                case "categories":
                    List<String> categories = productAIService.categorizeProduct(product);
                    result = Map.of("categories", categories, "type", "categories");
                    break;
                case "tags":
                    List<String> tags = productAIService.generateProductTags(product);
                    result = Map.of("tags", tags, "type", "tags");
                    break;
                default:
                    log.warn("Unknown content type: {}", contentType);
                    result = Map.of("error", "Unknown content type");
            }
            
            log.debug("Successfully generated {} content for product: {}", contentType, productId);
            return result;
            
        } catch (Exception e) {
            log.error("Error generating {} content for product: {}", contentType, productId, e);
            return Map.of("error", "Failed to generate content");
        }
    }
    
    /**
     * Index a product for AI search
     * 
     * @param productId the product ID
     * @return success status
     */
    public boolean indexProduct(String productId) {
        try {
            log.debug("Indexing product for AI search: {}", productId);
            
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                log.warn("Product not found for indexing: {}", productId);
                return false;
            }
            
            productAIService.indexProduct(productOpt.get());
            
            log.debug("Successfully indexed product: {}", productId);
            return true;
            
        } catch (Exception e) {
            log.error("Error indexing product: {}", productId, e);
            return false;
        }
    }
    
    /**
     * Remove a product from AI index
     * 
     * @param productId the product ID
     * @return success status
     */
    public boolean removeProductFromIndex(String productId) {
        try {
            log.debug("Removing product from AI index: {}", productId);
            
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                log.warn("Product not found for removal: {}", productId);
                return false;
            }
            
            productAIService.removeProductFromIndex(productOpt.get());
            
            log.debug("Successfully removed product from index: {}", productId);
            return true;
            
        } catch (Exception e) {
            log.error("Error removing product from index: {}", productId, e);
            return false;
        }
    }
    
    /**
     * Get AI statistics for products
     * 
     * @return AI statistics
     */
    public Map<String, Object> getAIStatistics() {
        try {
            log.debug("Getting AI statistics for products");
            
            long totalProducts = productRepository.count();
            long productsWithAIDescription = productRepository.findWithAIGeneratedContent().size();
            long productsWithAICategories = productRepository.findByAICategory("").size();
            long productsWithAITags = productRepository.findByAITag("").size();
            
            Map<String, Object> stats = Map.of(
                "totalProducts", totalProducts,
                "productsWithAIDescription", productsWithAIDescription,
                "productsWithAICategories", productsWithAICategories,
                "productsWithAITags", productsWithAITags,
                "aiDescriptionPercentage", totalProducts > 0 ? (productsWithAIDescription * 100.0 / totalProducts) : 0.0,
                "aiCategoriesPercentage", totalProducts > 0 ? (productsWithAICategories * 100.0 / totalProducts) : 0.0,
                "aiTagsPercentage", totalProducts > 0 ? (productsWithAITags * 100.0 / totalProducts) : 0.0
            );
            
            log.debug("AI statistics generated successfully");
            return stats;
            
        } catch (Exception e) {
            log.error("Error getting AI statistics", e);
            return Map.of("error", "Failed to get statistics");
        }
    }
}