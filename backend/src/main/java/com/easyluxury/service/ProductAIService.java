package com.easyluxury.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.rag.RAGService;
import com.easyluxury.ai.config.EasyLuxuryAIConfig;
import com.easyluxury.entity.Product;
import com.easyluxury.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Product AI Service
 * 
 * Provides AI-powered functionality for products including search, recommendations,
 * content generation, categorization, and tagging using the AI infrastructure module.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAIService {
    
    private final AICoreService aiCoreService;
    private final RAGService ragService;
    private final ProductRepository productRepository;
    private final EasyLuxuryAIConfig.EasyLuxuryAISettings aiSettings;
    
    /**
     * Generate AI-powered product description
     * 
     * @param product the product to generate description for
     * @return AI-generated description
     */
    @Transactional
    public String generateProductDescription(Product product) {
        if (!aiSettings.getEnableProductDescriptionGeneration() || 
            !aiSettings.getProduct().getDescriptionGeneration().getEnabled()) {
            log.debug("Product description generation is disabled");
            return product.getDescription();
        }
        
        try {
            log.debug("Generating AI description for product: {}", product.getName());
            
            String prompt = String.format(
                "Generate a luxury product description for: %s. " +
                "Brand: %s, Category: %s, Material: %s, Color: %s. " +
                "Style: %s, Max length: %d characters. " +
                "Make it compelling and luxurious.",
                product.getName(),
                product.getBrand() != null ? product.getBrand() : "Unknown",
                product.getCategory(),
                product.getMaterial() != null ? product.getMaterial() : "Unknown",
                product.getColor() != null ? product.getColor() : "Unknown",
                aiSettings.getProduct().getDescriptionGeneration().getStyle(),
                aiSettings.getProduct().getDescriptionGeneration().getMaxLength()
            );
            
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a luxury product copywriter. Create compelling, elegant descriptions that highlight the premium quality and exclusivity of luxury products.")
                .model(aiSettings.getDefaultAIModel())
                .maxTokens(aiSettings.getMaxTokens())
                .temperature(aiSettings.getTemperature())
                .entityType("product")
                .purpose("description_generation")
                .build();
            
            AIGenerationResponse response = aiCoreService.generateContent(request);
            
            // Update product with AI-generated description
            product.setAiGeneratedDescription(response.getContent());
            productRepository.save(product);
            
            log.debug("Successfully generated AI description for product: {}", product.getName());
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error generating product description for product: {}", product.getName(), e);
            return product.getDescription();
        }
    }
    
    /**
     * Categorize product using AI
     * 
     * @param product the product to categorize
     * @return AI-generated categories
     */
    @Transactional
    public List<String> categorizeProduct(Product product) {
        if (!aiSettings.getEnableProductCategorization() || 
            !aiSettings.getProduct().getCategorization().getEnabled()) {
            log.debug("Product categorization is disabled");
            return List.of(product.getCategory());
        }
        
        try {
            log.debug("Categorizing product using AI: {}", product.getName());
            
            String prompt = String.format(
                "Categorize this luxury product: %s. " +
                "Description: %s, Brand: %s, Current category: %s. " +
                "Available categories: %s. " +
                "Return only the most appropriate categories, separated by commas.",
                product.getName(),
                product.getDescription() != null ? product.getDescription() : "",
                product.getBrand() != null ? product.getBrand() : "Unknown",
                product.getCategory(),
                aiSettings.getProduct().getCategorization().getCategories()
            );
            
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a luxury product categorization expert. Analyze products and assign them to the most appropriate luxury categories.")
                .model(aiSettings.getDefaultAIModel())
                .maxTokens(500)
                .temperature(0.1)
                .entityType("product")
                .purpose("categorization")
                .build();
            
            AIGenerationResponse response = aiCoreService.generateContent(request);
            
            List<String> categories = List.of(response.getContent().split(","))
                .stream()
                .map(String::trim)
                .collect(Collectors.toList());
            
            // Update product with AI-generated categories
            product.setAiCategories(String.join(",", categories));
            productRepository.save(product);
            
            log.debug("Successfully categorized product: {} with categories: {}", product.getName(), categories);
            return categories;
            
        } catch (Exception e) {
            log.error("Error categorizing product: {}", product.getName(), e);
            return List.of(product.getCategory());
        }
    }
    
    /**
     * Generate AI tags for product
     * 
     * @param product the product to tag
     * @return AI-generated tags
     */
    @Transactional
    public List<String> generateProductTags(Product product) {
        if (!aiSettings.getEnableProductTagging() || 
            !aiSettings.getProduct().getTagging().getEnabled()) {
            log.debug("Product tagging is disabled");
            return product.getTags() != null ? product.getTags() : List.of();
        }
        
        try {
            log.debug("Generating AI tags for product: {}", product.getName());
            
            String prompt = String.format(
                "Generate relevant tags for this luxury product: %s. " +
                "Description: %s, Brand: %s, Category: %s, Material: %s, Color: %s. " +
                "Tag types to consider: %s. " +
                "Maximum %d tags. Return only the tags, separated by commas.",
                product.getName(),
                product.getDescription() != null ? product.getDescription() : "",
                product.getBrand() != null ? product.getBrand() : "Unknown",
                product.getCategory(),
                product.getMaterial() != null ? product.getMaterial() : "Unknown",
                product.getColor() != null ? product.getColor() : "Unknown",
                aiSettings.getProduct().getTagging().getTagTypes(),
                aiSettings.getProduct().getTagging().getMaxTags()
            );
            
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a luxury product tagging expert. Generate relevant, descriptive tags that help customers find and understand luxury products.")
                .model(aiSettings.getDefaultAIModel())
                .maxTokens(300)
                .temperature(0.2)
                .entityType("product")
                .purpose("tagging")
                .build();
            
            AIGenerationResponse response = aiCoreService.generateContent(request);
            
            List<String> tags = List.of(response.getContent().split(","))
                .stream()
                .map(String::trim)
                .limit(aiSettings.getProduct().getTagging().getMaxTags())
                .collect(Collectors.toList());
            
            // Update product with AI-generated tags
            product.setAiTags(String.join(",", tags));
            productRepository.save(product);
            
            log.debug("Successfully generated tags for product: {} with tags: {}", product.getName(), tags);
            return tags;
            
        } catch (Exception e) {
            log.error("Error generating tags for product: {}", product.getName(), e);
            return product.getTags() != null ? product.getTags() : List.of();
        }
    }
    
    /**
     * Search products using AI semantic search
     * 
     * @param query the search query
     * @param limit maximum number of results
     * @return list of matching products
     */
    public List<Product> searchProducts(String query, int limit) {
        if (!aiSettings.getEnableAISearch() || 
            !aiSettings.getProduct().getSearch().getEnabled()) {
            log.debug("AI product search is disabled");
            return List.of();
        }
        
        try {
            log.debug("Performing AI search for products with query: {}", query);
            
            AISearchRequest request = AISearchRequest.builder()
                .query(query)
                .entityType("product")
                .limit(Math.min(limit, aiSettings.getProduct().getSearch().getMaxResults()))
                .threshold(aiSettings.getProduct().getSearch().getSimilarityThreshold())
                .build();
            
            AISearchResponse response = aiCoreService.performSearch(request);
            
            // Convert search results to products
            List<Product> products = response.getResults().stream()
                .map(result -> {
                    // In a real implementation, you would fetch the actual product from the database
                    // using the ID from the search result
                    String productId = (String) result.get("id");
                    return productRepository.findById(productId).orElse(null);
                })
                .filter(product -> product != null)
                .collect(Collectors.toList());
            
            log.debug("AI search completed with {} results", products.size());
            return products;
            
        } catch (Exception e) {
            log.error("Error performing AI search for products", e);
            return List.of();
        }
    }
    
    /**
     * Generate product recommendations
     * 
     * @param product the product to get recommendations for
     * @param limit maximum number of recommendations
     * @return list of recommended products
     */
    public List<Product> getProductRecommendations(Product product, int limit) {
        if (!aiSettings.getEnableProductRecommendations() || 
            !aiSettings.getProduct().getRecommendations().getEnabled()) {
            log.debug("Product recommendations are disabled");
            return List.of();
        }
        
        try {
            log.debug("Generating recommendations for product: {}", product.getName());
            
            // Use RAG to find similar products
            String query = String.format(
                "Find similar luxury products to: %s. " +
                "Category: %s, Brand: %s, Material: %s, Price range: %s",
                product.getName(),
                product.getCategory(),
                product.getBrand() != null ? product.getBrand() : "Unknown",
                product.getMaterial() != null ? product.getMaterial() : "Unknown",
                product.getPriceRange() != null ? product.getPriceRange() : "Unknown"
            );
            
            AISearchResponse searchResponse = ragService.performRAGQuery(
                query, 
                "product", 
                Math.min(limit, aiSettings.getProduct().getRecommendations().getMaxRecommendations())
            );
            
            // Convert search results to products
            List<Product> recommendations = searchResponse.getResults().stream()
                .map(result -> {
                    String productId = (String) result.get("id");
                    return productRepository.findById(productId).orElse(null);
                })
                .filter(p -> p != null && !p.getId().equals(product.getId())) // Exclude the original product
                .limit(limit)
                .collect(Collectors.toList());
            
            log.debug("Generated {} recommendations for product: {}", recommendations.size(), product.getName());
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating recommendations for product: {}", product.getName(), e);
            return List.of();
        }
    }
    
    /**
     * Index product for AI search
     * 
     * @param product the product to index
     */
    @Transactional
    public void indexProduct(Product product) {
        try {
            log.debug("Indexing product for AI search: {}", product.getName());
            
            // Generate embedding for the product
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(product.getSearchableText())
                .entityType("product")
                .entityId(product.getId())
                .metadata(product.getAIMetadata().toString())
                .build();
            
            AIEmbeddingResponse embeddingResponse = aiCoreService.generateEmbedding(embeddingRequest);
            
            // Store in vector database via RAG service
            ragService.indexContent(
                "product",
                product.getId(),
                product.getSearchableText(),
                product.getAIMetadata()
            );
            
            // Update product with search vector
            product.setSearchVector(embeddingResponse.getEmbedding().toString());
            productRepository.save(product);
            
            log.debug("Successfully indexed product: {}", product.getName());
            
        } catch (Exception e) {
            log.error("Error indexing product: {}", product.getName(), e);
        }
    }
    
    /**
     * Remove product from AI index
     * 
     * @param product the product to remove
     */
    @Transactional
    public void removeProductFromIndex(Product product) {
        try {
            log.debug("Removing product from AI index: {}", product.getName());
            
            ragService.removeContent("product", product.getId());
            
            // Clear search vector
            product.setSearchVector(null);
            productRepository.save(product);
            
            log.debug("Successfully removed product from index: {}", product.getName());
            
        } catch (Exception e) {
            log.error("Error removing product from index: {}", product.getName(), e);
        }
    }
    
    /**
     * Get AI insights for a product
     * 
     * @param product the product to analyze
     * @return AI insights
     */
    public Map<String, Object> getProductInsights(Product product) {
        try {
            log.debug("Generating AI insights for product: {}", product.getName());
            
            String prompt = String.format(
                "Analyze this luxury product and provide insights: %s. " +
                "Description: %s, Brand: %s, Category: %s, Price: %s, " +
                "View count: %d, Purchase count: %d. " +
                "Provide insights on market positioning, target audience, and optimization opportunities.",
                product.getName(),
                product.getDescription() != null ? product.getDescription() : "",
                product.getBrand() != null ? product.getBrand() : "Unknown",
                product.getCategory(),
                product.getPrice() != null ? product.getPrice().toString() : "Unknown",
                product.getViewCount() != null ? product.getViewCount() : 0L,
                product.getPurchaseCount() != null ? product.getPurchaseCount() : 0L
            );
            
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a luxury retail analyst. Provide detailed insights about product positioning, market opportunities, and optimization recommendations.")
                .model(aiSettings.getDefaultAIModel())
                .maxTokens(1000)
                .temperature(0.3)
                .entityType("product")
                .purpose("insights")
                .build();
            
            AIGenerationResponse response = aiCoreService.generateContent(request);
            
            return Map.of(
                "insights", response.getContent(),
                "productId", product.getId(),
                "productName", product.getName(),
                "generatedAt", java.time.LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Error generating insights for product: {}", product.getName(), e);
            return Map.of(
                "error", "Failed to generate insights",
                "productId", product.getId(),
                "productName", product.getName()
            );
        }
    }
}