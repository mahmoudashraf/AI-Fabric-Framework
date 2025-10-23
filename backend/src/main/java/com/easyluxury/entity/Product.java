package com.easyluxury.entity;

import com.ai.infrastructure.annotation.AICapable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product Entity
 * 
 * Clean domain entity with single AI annotation.
 * AI behavior is defined in configuration file.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(entityType = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;
    
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    private String description;
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    @Column(name = "category", nullable = false)
    private String category;
    
    @Size(max = 100, message = "Brand must not exceed 100 characters")
    @Column(name = "brand")
    private String brand;
    
    @Size(max = 50, message = "SKU must not exceed 50 characters")
    @Column(name = "sku", unique = true)
    private String sku;
    
    @Column(name = "stock_quantity")
    private Integer stockQuantity;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "is_featured")
    private Boolean isFeatured = false;
    
    @Column(name = "is_luxury")
    private Boolean isLuxury = true;
    
    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    private List<String> imageUrls;
    
    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private List<String> tags;
    
    @ElementCollection
    @MapKeyColumn(name = "attribute_name")
    @Column(name = "attribute_value")
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    private Map<String, String> attributes;
    
    @Column(name = "weight")
    private BigDecimal weight;
    
    @Column(name = "dimensions")
    private String dimensions;
    
    @Column(name = "material")
    private String material;
    
    @Column(name = "color")
    private String color;
    
    @Column(name = "size")
    private String size;
    
    @Column(name = "season")
    private String season;
    
    @Column(name = "occasion")
    private String occasion;
    
    @Column(name = "price_range")
    private String priceRange;
    
    @Column(name = "ai_generated_description")
    private String aiGeneratedDescription;
    
    @Column(name = "ai_categories")
    private String aiCategories;
    
    @Column(name = "ai_tags")
    private String aiTags;
    
    @Column(name = "search_vector")
    private String searchVector;
    
    @Column(name = "recommendation_score")
    private Double recommendationScore;
    
    @Column(name = "view_count")
    private Long viewCount = 0L;
    
    @Column(name = "purchase_count")
    private Long purchaseCount = 0L;
    
    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;
    
    @Column(name = "last_purchased_at")
    private LocalDateTime lastPurchasedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Helper methods for AI operations
    
    /**
     * Get the primary searchable text for AI operations
     */
    public String getSearchableText() {
        StringBuilder text = new StringBuilder();
        text.append(name).append(" ");
        if (description != null) {
            text.append(description).append(" ");
        }
        if (brand != null) {
            text.append(brand).append(" ");
        }
        if (category != null) {
            text.append(category).append(" ");
        }
        if (material != null) {
            text.append(material).append(" ");
        }
        if (color != null) {
            text.append(color).append(" ");
        }
        if (tags != null) {
            text.append(String.join(" ", tags)).append(" ");
        }
        if (aiGeneratedDescription != null) {
            text.append(aiGeneratedDescription).append(" ");
        }
        if (aiTags != null) {
            text.append(aiTags).append(" ");
        }
        return text.toString().trim();
    }
    
    /**
     * Get metadata for AI operations
     */
    public Map<String, Object> getAIMetadata() {
        return Map.of(
            "id", id,
            "name", name,
            "category", category != null ? category : "",
            "brand", brand != null ? brand : "",
            "price", price != null ? price.toString() : "0",
            "isLuxury", isLuxury != null ? isLuxury : true,
            "isFeatured", isFeatured != null ? isFeatured : false,
            "stockQuantity", stockQuantity != null ? stockQuantity : 0,
            "viewCount", viewCount != null ? viewCount : 0L,
            "purchaseCount", purchaseCount != null ? purchaseCount : 0L
        );
    }
}