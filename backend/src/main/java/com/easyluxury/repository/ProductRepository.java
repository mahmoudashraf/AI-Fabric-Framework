package com.easyluxury.repository;

import com.easyluxury.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Product Repository
 * 
 * Repository interface for Product entity with custom query methods
 * for AI-powered search and recommendations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    
    /**
     * Find products by category
     * 
     * @param category the product category
     * @return list of products in the category
     */
    List<Product> findByCategory(String category);
    
    /**
     * Find products by brand
     * 
     * @param brand the product brand
     * @return list of products from the brand
     */
    List<Product> findByBrand(String brand);
    
    /**
     * Find products by price range
     * 
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @return list of products in the price range
     */
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);
    
    /**
     * Find active products
     * 
     * @param isActive whether the product is active
     * @return list of active/inactive products
     */
    List<Product> findByIsActive(Boolean isActive);
    
    /**
     * Find featured products
     * 
     * @param isFeatured whether the product is featured
     * @return list of featured products
     */
    List<Product> findByIsFeatured(Boolean isFeatured);
    
    /**
     * Find luxury products
     * 
     * @param isLuxury whether the product is luxury
     * @return list of luxury products
     */
    List<Product> findByIsLuxury(Boolean isLuxury);
    
    /**
     * Find products by SKU
     * 
     * @param sku the product SKU
     * @return optional product with the SKU
     */
    Optional<Product> findBySku(String sku);
    
    /**
     * Find products by name containing (case-insensitive)
     * 
     * @param name the name to search for
     * @return list of products with matching names
     */
    List<Product> findByNameContainingIgnoreCase(String name);
    
    /**
     * Find products by description containing (case-insensitive)
     * 
     * @param description the description to search for
     * @return list of products with matching descriptions
     */
    List<Product> findByDescriptionContainingIgnoreCase(String description);
    
    /**
     * Find products by tag
     * 
     * @param tag the tag to search for
     * @return list of products with the tag
     */
    @Query("SELECT p FROM Product p JOIN p.tags t WHERE t = :tag")
    List<Product> findByTag(@Param("tag") String tag);
    
    /**
     * Find products by material
     * 
     * @param material the material to search for
     * @return list of products with the material
     */
    List<Product> findByMaterial(String material);
    
    /**
     * Find products by color
     * 
     * @param color the color to search for
     * @return list of products with the color
     */
    List<Product> findByColor(String color);
    
    /**
     * Find products by size
     * 
     * @param size the size to search for
     * @return list of products with the size
     */
    List<Product> findBySize(String size);
    
    /**
     * Find products by season
     * 
     * @param season the season to search for
     * @return list of products for the season
     */
    List<Product> findBySeason(String season);
    
    /**
     * Find products by occasion
     * 
     * @param occasion the occasion to search for
     * @return list of products for the occasion
     */
    List<Product> findByOccasion(String occasion);
    
    /**
     * Find products by price range
     * 
     * @param priceRange the price range to search for
     * @return list of products in the price range
     */
    List<Product> findByPriceRange(String priceRange);
    
    /**
     * Find products with stock available
     * 
     * @return list of products with stock > 0
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0")
    List<Product> findInStock();
    
    /**
     * Find products with no stock
     * 
     * @return list of products with stock = 0
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity = 0 OR p.stockQuantity IS NULL")
    List<Product> findOutOfStock();
    
    /**
     * Find products by multiple criteria
     * 
     * @param category the product category
     * @param brand the product brand
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @param isActive whether the product is active
     * @return list of products matching all criteria
     */
    @Query("SELECT p FROM Product p WHERE " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:brand IS NULL OR p.brand = :brand) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive)")
    List<Product> findByMultipleCriteria(
        @Param("category") String category,
        @Param("brand") String brand,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        @Param("isActive") Boolean isActive
    );
    
    /**
     * Find most viewed products
     * 
     * @param limit maximum number of results
     * @return list of most viewed products
     */
    @Query("SELECT p FROM Product p ORDER BY p.viewCount DESC")
    List<Product> findMostViewed(int limit);
    
    /**
     * Find most purchased products
     * 
     * @param limit maximum number of results
     * @return list of most purchased products
     */
    @Query("SELECT p FROM Product p ORDER BY p.purchaseCount DESC")
    List<Product> findMostPurchased(int limit);
    
    /**
     * Find recently created products
     * 
     * @param limit maximum number of results
     * @return list of recently created products
     */
    @Query("SELECT p FROM Product p ORDER BY p.createdAt DESC")
    List<Product> findRecentlyCreated(int limit);
    
    /**
     * Find products with AI-generated content
     * 
     * @return list of products with AI-generated descriptions
     */
    @Query("SELECT p FROM Product p WHERE p.aiGeneratedDescription IS NOT NULL")
    List<Product> findWithAIGeneratedContent();
    
    /**
     * Find products by AI category
     * 
     * @param aiCategory the AI-generated category
     * @return list of products with the AI category
     */
    @Query("SELECT p FROM Product p WHERE p.aiCategories LIKE %:aiCategory%")
    List<Product> findByAICategory(@Param("aiCategory") String aiCategory);
    
    /**
     * Find products by AI tag
     * 
     * @param aiTag the AI-generated tag
     * @return list of products with the AI tag
     */
    @Query("SELECT p FROM Product p WHERE p.aiTags LIKE %:aiTag%")
    List<Product> findByAITag(@Param("aiTag") String aiTag);
    
    /**
     * Count products by category
     * 
     * @param category the product category
     * @return number of products in the category
     */
    long countByCategory(String category);
    
    /**
     * Count products by brand
     * 
     * @param brand the product brand
     * @return number of products from the brand
     */
    long countByBrand(String brand);
    
    /**
     * Count active products
     * 
     * @param isActive whether the product is active
     * @return number of active/inactive products
     */
    long countByIsActive(Boolean isActive);
}