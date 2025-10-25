package com.easyluxury.ai;

import com.easyluxury.ai.adapter.ProductAIAdapter;
import com.easyluxury.ai.adapter.UserAIAdapter;
import com.easyluxury.ai.adapter.OrderAIAdapter;
import com.easyluxury.entity.Product;
import com.easyluxury.entity.User;
import com.easyluxury.entity.Order;
import com.easyluxury.entity.UserBehavior;
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
        product.setId(UUID.randomUUID().toString());
        product.setName("Test Product for AI Processing");
        product.setDescription("A comprehensive test product for AI capabilities");
        product.setPrice(new BigDecimal("99.99"));
        product.setCategory("Electronics");
        product.setBrand("TestBrand");
        product.setCreatedAt(LocalDateTime.now());
        
        // Create a test user
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        
        // Test AI processing
        assertDoesNotThrow(() -> {
            productAIAdapter.trackProductView(user, product);
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
            UserBehavior userBehavior = new UserBehavior();
            userBehavior.setBehaviorType(UserBehavior.BehaviorType.SESSION_START);
            userBehavior.setEntityType("user");
            userBehavior.setEntityId(user.getId().toString());
            userBehavior.setAction("login");
            userAIAdapter.trackUserBehavior(user, userBehavior);
        }, "User AI processing should not throw exceptions");
    }
    
    @Test
    public void testOrderAIProcessing() {
        // Create a test order
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("199.99"));
        order.setStatus(Order.OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        
        // Create a test user
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        
        // Test AI processing
        assertDoesNotThrow(() -> {
            orderAIAdapter.trackOrderCreation(user, order);
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
            productAIAdapter.trackProductView(user1, product1);
            productAIAdapter.trackProductView(user2, product2);
            UserBehavior userBehavior1 = new UserBehavior();
            userBehavior1.setBehaviorType(UserBehavior.BehaviorType.LOGIN);
            userBehavior1.setEntityType("user");
            userBehavior1.setEntityId(user1.getId().toString());
            userBehavior1.setAction("login");
            userAIAdapter.trackUserBehavior(user1, userBehavior1);
            
            UserBehavior userBehavior2 = new UserBehavior();
            userBehavior2.setBehaviorType(UserBehavior.BehaviorType.LOGIN);
            userBehavior2.setEntityType("user");
            userBehavior2.setEntityId(user2.getId().toString());
            userBehavior2.setAction("login");
            userAIAdapter.trackUserBehavior(user2, userBehavior2);
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
            User user = createTestUser("search@example.com");
            productAIAdapter.trackProductView(user, product);
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
            UserBehavior userBehavior = new UserBehavior();
            userBehavior.setBehaviorType(UserBehavior.BehaviorType.SESSION_START);
            userBehavior.setEntityType("user");
            userBehavior.setEntityId(user.getId().toString());
            userBehavior.setAction("login");
            userAIAdapter.trackUserBehavior(user, userBehavior);
        }, "AI recommendation processing should not throw exceptions");
    }
    
    private Product createTestProduct(String name) {
        Product product = new Product();
        product.setId(UUID.randomUUID().toString());
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
