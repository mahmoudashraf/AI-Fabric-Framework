package com.easyluxury.ai.mapper;

import com.easyluxury.ai.dto.ProductAIGenerationRequest;
import com.easyluxury.ai.dto.ProductAIGenerationResponse;
import com.easyluxury.ai.dto.ProductAIRecommendationRequest;
import com.easyluxury.ai.dto.ProductAIRecommendationResponse;
import com.easyluxury.ai.dto.ProductAISearchRequest;
import com.easyluxury.ai.dto.ProductAISearchResponse;
import com.easyluxury.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * ProductAIMapper
 * 
 * MapStruct mapper for Product AI DTOs and entities.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Mapper(componentModel = "spring")
public interface ProductAIMapper {
    
    ProductAIMapper INSTANCE = Mappers.getMapper(ProductAIMapper.class);
    
    /**
     * Map Product to ProductAIRecommendationItem
     */
    @Mapping(target = "productId", source = "id")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "imageUrl", expression = "java(product.getImageUrls() != null && !product.getImageUrls().isEmpty() ? product.getImageUrls().get(0) : null)")
    @Mapping(target = "recommendationScore", source = "recommendationScore")
    @Mapping(target = "reason", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    ProductAIRecommendationResponse.ProductAIRecommendationItem toProductAIRecommendationItem(Product product);
    
    /**
     * Map list of Product to list of ProductAIRecommendationItem
     */
    List<ProductAIRecommendationResponse.ProductAIRecommendationItem> toProductAIRecommendationItemList(List<Product> products);
    
    /**
     * Map Product to ProductAIGenerationResponse
     */
    @Mapping(target = "generatedContent", ignore = true)
    @Mapping(target = "originalContent", ignore = true)
    @Mapping(target = "confidenceScore", ignore = true)
    @Mapping(target = "generationTimestamp", ignore = true)
    @Mapping(target = "model", ignore = true)
    @Mapping(target = "processingTimeMs", ignore = true)
    @Mapping(target = "requestId", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    ProductAIGenerationResponse toProductAIGenerationResponse(Product product);
    
    /**
     * Map list of Product to list of ProductAIGenerationResponse
     */
    List<ProductAIGenerationResponse> toProductAIGenerationResponseList(List<Product> products);
}