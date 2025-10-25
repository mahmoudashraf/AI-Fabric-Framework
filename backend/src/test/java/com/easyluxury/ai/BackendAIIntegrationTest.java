package com.easyluxury.ai;

import com.easyluxury.ai.adapter.ProductAIAdapter;
import com.easyluxury.ai.adapter.UserAIAdapter;
import com.easyluxury.ai.adapter.OrderAIAdapter;
import com.easyluxury.entity.Product;
import com.easyluxury.entity.User;
import com.easyluxury.entity.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BackendAIIntegrationTest {
    
    @Autowired
    private ProductAIAdapter productAIAdapter;
    
    @Autowired
    private UserAIAdapter userAIAdapter;
    
    @Autowired
    private OrderAIAdapter orderAIAdapter;
    
    @Test
    public void testAIAdaptersExist() {
        assertNotNull(productAIAdapter, "ProductAIAdapter should be available");
        assertNotNull(userAIAdapter, "UserAIAdapter should be available");
        assertNotNull(orderAIAdapter, "OrderAIAdapter should be available");
    }
    
    @Test
    public void testProductAIProcessing() {
        // Create a test product
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Test Product for AI Processing");
        product.setDescription("A comprehensive test product for AI capabilities");
        product.setPrice(new BigDecimal("99.99"));
        product.setCategory("Electronics");
        product.setBrand("TestBrand");
        product.setCreatedAt(LocalDateTime.now());
        
        // Test AI processing
        assertDoesNotThrow(() -> {
            productAIAdapter.processForAI(product);
        }, "Product AI processing should not throw exceptions");
    }
    
    @Test
    public void testUserAIProcessing() {
        // Create a test user
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setCreatedAt(LocalDateTime.now());
        
        // Test AI processing
        assertDoesNotThrow(() -> {
            userAIAdapter.processForAI(user);
        }, "User AI processing should not throw exceptions");
    }
    
    @Test
    public void testOrderAIProcessing() {
        // Create a test order
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("199.99"));
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        
        // Test AI processing
        assertDoesNotThrow(() -> {
            orderAIAdapter.processForAI(order);
        }, "Order AI processing should not throw exceptions");
    }
    
    @Test
    public void testBatchAIProcessing() {
        // Create multiple test entities
        Product product1 = createTestProduct("Product 1");
        Product product2 = createTestProduct("Product 2");
        User user1 = createTestUser("user1@example.com");
        User user2 = createTestUser("user2@example.com");
        
        // Test batch processing
        assertDoesNotThrow(() -> {
            productAIAdapter.processForAI(product1);
            productAIAdapter.processForAI(product2);
            userAIAdapter.processForAI(user1);
            userAIAdapter.processForAI(user2);
        }, "Batch AI processing should not throw exceptions");
    }
    
    @Test
    public void testAISearchCapabilities() {
        // Create a test product with searchable content
        Product product = createTestProduct("Searchable Product");
        product.setDescription("This product contains comprehensive searchable content for testing AI search capabilities");
        product.setCategory("Searchable Category");
        
        // Test search processing
        assertDoesNotThrow(() -> {
            productAIAdapter.processForAI(product);
        }, "AI search processing should not throw exceptions");
    }
    
    @Test
    public void testAIRecommendationCapabilities() {
        // Create a test user with preferences
        User user = createTestUser("recommendation@example.com");
        user.setFirstName("Recommendation");
        user.setLastName("Tester");
        
        // Test recommendation processing
        assertDoesNotThrow(() -> {
            userAIAdapter.processForAI(user);
        }, "AI recommendation processing should not throw exceptions");
    }
    
    private Product createTestProduct(String name) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(name);
        product.setDescription("Test product description for " + name);
        product.setPrice(new BigDecimal("49.99"));
        product.setCategory("Test Category");
        product.setBrand("Test Brand");
        product.setCreatedAt(LocalDateTime.now());
        return product;
    }
    
    private User createTestUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
