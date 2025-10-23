package com.easyluxury.ai;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.easyluxury.entity.Product;
import com.easyluxury.service.ProductService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Annotation Integration Test
 * 
 * Tests the AI annotation system in the backend.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
class AIAnnotationIntegrationTest {
    
    @Test
    void testProductEntityAnnotation() {
        // Test Product entity has @AICapable annotation
        AICapable annotation = Product.class.getAnnotation(AICapable.class);
        assertNotNull(annotation);
        assertEquals("product", annotation.entityType());
    }
    
    @Test
    void testProductServiceAnnotations() {
        // Test ProductService methods have AI annotations
        try {
            var createMethod = ProductService.class.getMethod("createProduct", Product.class);
            var createAnnotation = createMethod.getAnnotation(AIProcess.class);
            assertNotNull(createAnnotation);
            assertEquals("product", createAnnotation.entityType());
            assertEquals("create", createAnnotation.processType());
            
            var updateMethod = ProductService.class.getMethod("updateProduct", String.class, Product.class);
            var updateAnnotation = updateMethod.getAnnotation(AIProcess.class);
            assertNotNull(updateAnnotation);
            assertEquals("product", updateAnnotation.entityType());
            assertEquals("update", updateAnnotation.processType());
            
        } catch (NoSuchMethodException e) {
            fail("Method not found: " + e.getMessage());
        }
    }
}
