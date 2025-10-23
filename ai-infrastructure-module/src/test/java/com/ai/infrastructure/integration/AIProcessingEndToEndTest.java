package com.ai.infrastructure.integration;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Tests for AI Processing
 * Tests the complete flow from entity processing to search indexing
 */
@ExtendWith(MockitoExtension.class)
public class AIProcessingEndToEndTest {

    @Mock
    private AIEntityConfigurationLoader configLoader;

    @Mock
    private AISearchableEntityRepository searchRepository;

    private AICapabilityService capabilityService;

    @BeforeEach
    public void setUp() {
        capabilityService = new AICapabilityService(configLoader, searchRepository);
    }

    @Test
    public void testProductEntityProcessing() {
        // Given
        ProductEntity product = new ProductEntity();
        product.setId(1L);
        product.setName("Luxury Watch");
        product.setDescription("Premium Swiss watch with diamond bezel");
        product.setPrice(5000.0);
        product.setCategory("Watches");
        product.setBrand("Rolex");

        AIEntityConfig config = createProductConfig();
        when(configLoader.getEntityConfig("product")).thenReturn(config);

        // When
        capabilityService.processEntity(product, "create");

        // Then
        verify(searchRepository, times(1)).save(any(AISearchableEntity.class));
    }

    @Test
    public void testUserEntityProcessing() {
        // Given
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");
        user.setPhone("+1234567890");

        AIEntityConfig config = createUserConfig();
        when(configLoader.getEntityConfig("user")).thenReturn(config);

        // When
        capabilityService.processEntity(user, "create");

        // Then
        verify(searchRepository, times(1)).save(any(AISearchableEntity.class));
    }

    @Test
    public void testOrderEntityProcessing() {
        // Given
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        order.setUserId(1L);
        order.setTotalAmount(5000.0);
        order.setStatus("PENDING");
        order.setNotes("Express delivery requested");
        order.setShippingAddress("123 Main St, New York, NY 10001");

        AIEntityConfig config = createOrderConfig();
        when(configLoader.getEntityConfig("order")).thenReturn(config);

        // When
        capabilityService.processEntity(order, "create");

        // Then
        verify(searchRepository, times(1)).save(any(AISearchableEntity.class));
    }

    @Test
    public void testEntityUpdateProcessing() {
        // Given
        ProductEntity product = new ProductEntity();
        product.setId(1L);
        product.setName("Updated Luxury Watch");
        product.setDescription("Updated description");

        AIEntityConfig config = createProductConfig();
        when(configLoader.getEntityConfig("product")).thenReturn(config);

        // When
        capabilityService.processEntity(product, "update");

        // Then
        verify(searchRepository, times(1)).save(any(AISearchableEntity.class));
    }

    @Test
    public void testEntityDeletionProcessing() {
        // Given
        Long entityId = 1L;
        String entityType = "product";

        // When
        capabilityService.removeFromIndex(entityId, entityType);

        // Then
        verify(searchRepository, times(1)).deleteByEntityIdAndEntityType(entityId, entityType);
    }

    @Test
    public void testSearchFunctionality() {
        // Given
        String searchQuery = "luxury watch";
        String entityType = "product";

        List<AISearchableEntity> mockResults = Arrays.asList(
            createMockSearchableEntity(1L, "product", "Luxury Watch", new double[]{0.1, 0.2, 0.3}),
            createMockSearchableEntity(2L, "product", "Premium Watch", new double[]{0.2, 0.3, 0.4})
        );

        when(searchRepository.findByEntityType(entityType)).thenReturn(mockResults);

        // When
        List<AISearchableEntity> results = capabilityService.searchEntities(searchQuery, entityType);

        // Then
        assertNotNull(results);
        verify(searchRepository, times(1)).findByEntityType(entityType);
    }

    // Helper methods to create test entities and configurations
    private AIEntityConfig createProductConfig() {
        AIEntityConfig config = new AIEntityConfig();
        config.setEntityType("product");
        config.setSearchableFields(Arrays.asList(
            createSearchableField("name", 1.0),
            createSearchableField("description", 0.8),
            createSearchableField("category", 0.6)
        ));
        config.setEmbeddableFields(Arrays.asList(
            createEmbeddableField("name", "text"),
            createEmbeddableField("description", "text")
        ));
        return config;
    }

    private AIEntityConfig createUserConfig() {
        AIEntityConfig config = new AIEntityConfig();
        config.setEntityType("user");
        config.setSearchableFields(Arrays.asList(
            createSearchableField("firstName", 0.8),
            createSearchableField("lastName", 0.8),
            createSearchableField("email", 1.0)
        ));
        return config;
    }

    private AIEntityConfig createOrderConfig() {
        AIEntityConfig config = new AIEntityConfig();
        config.setEntityType("order");
        config.setSearchableFields(Arrays.asList(
            createSearchableField("status", 0.6),
            createSearchableField("notes", 0.8)
        ));
        return config;
    }

    private com.ai.infrastructure.dto.AISearchableField createSearchableField(String fieldName, Double weight) {
        com.ai.infrastructure.dto.AISearchableField field = new com.ai.infrastructure.dto.AISearchableField();
        field.setFieldName(fieldName);
        field.setWeight(weight);
        return field;
    }

    private com.ai.infrastructure.dto.AIEmbeddableField createEmbeddableField(String fieldName, String fieldType) {
        com.ai.infrastructure.dto.AIEmbeddableField field = new com.ai.infrastructure.dto.AIEmbeddableField();
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        return field;
    }

    private AISearchableEntity createMockSearchableEntity(Long entityId, String entityType, String content, double[] embeddings) {
        AISearchableEntity entity = new AISearchableEntity();
        entity.setEntityId(entityId);
        entity.setEntityType(entityType);
        entity.setContent(content);
        entity.setEmbeddings(Arrays.asList(
            Arrays.stream(embeddings).boxed().toArray(Double[]::new)
        ));
        return entity;
    }

    // Test entity classes
    @AICapable(entityType = "product")
    public static class ProductEntity {
        private Long id;
        private String name;
        private String description;
        private Double price;
        private String category;
        private String brand;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
    }

    @AICapable(entityType = "user")
    public static class UserEntity {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    @AICapable(entityType = "order")
    public static class OrderEntity {
        private Long id;
        private Long userId;
        private Double totalAmount;
        private String status;
        private String notes;
        private String shippingAddress;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public String getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    }
}