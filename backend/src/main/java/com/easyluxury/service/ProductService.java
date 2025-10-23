package com.easyluxury.service;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.easyluxury.entity.Product;
import com.easyluxury.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Product Service
 * 
 * Domain service with AI capabilities enabled via annotations.
 * AI processing happens automatically via AOP aspects.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    
    /**
     * Create a new product
     * AI processing: Automatic embedding generation, search indexing
     */
    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public Product createProduct(Product product) {
        log.info("Creating product: {}", product.getName());
        return productRepository.save(product);
    }
    
    /**
     * Update an existing product
     * AI processing: Automatic embedding regeneration, search re-indexing, analysis
     */
    @AIProcess(entityType = "product", processType = "update")
    @Transactional
    public Product updateProduct(String id, Product product) {
        log.info("Updating product: {}", id);
        
        Product existingProduct = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        
        existingProduct.setName(product.getName());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setCategory(product.getCategory());
        existingProduct.setTags(product.getTags());
        existingProduct.setIsActive(product.getIsActive());
        
        return productRepository.save(existingProduct);
    }
    
    /**
     * Delete a product
     * AI processing: Automatic removal from search index, cleanup
     */
    @AIProcess(entityType = "product", processType = "delete")
    @Transactional
    public void deleteProduct(String id) {
        log.info("Deleting product: {}", id);
        
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        
        productRepository.delete(product);
    }
    
    /**
     * Find product by ID
     */
    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }
    
    /**
     * Find all products
     */
    public List<Product> findAll() {
        return productRepository.findAll();
    }
    
    /**
     * Find products by category
     */
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }
    
    /**
     * Find available products
     */
    public List<Product> findAvailableProducts() {
        return productRepository.findByIsActive(true);
    }
}
