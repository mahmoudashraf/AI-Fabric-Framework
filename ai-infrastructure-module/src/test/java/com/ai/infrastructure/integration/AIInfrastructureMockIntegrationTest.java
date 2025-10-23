package com.ai.infrastructure.integration;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AISearchableField;
import com.ai.infrastructure.dto.AIEmbeddableField;
import com.ai.infrastructure.dto.AIMetadataField;
import com.ai.infrastructure.dto.AICrudOperation;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.aspect.AICapableAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock-based Integration Tests for AI Infrastructure Module
 * Tests the complete integration using mocks for external dependencies
 */
@ExtendWith(MockitoExtension.class)
public class AIInfrastructureMockIntegrationTest {

    @Mock
    private AIEntityConfigurationLoader configLoader;

    @Mock
    private AISearchableEntityRepository searchRepository;

    private AICapabilityService capabilityService;
    private AICapableAspect aspect;

    @BeforeEach
    public void setUp() {
        // Mock the required services
        com.ai.infrastructure.core.AIEmbeddingService embeddingService = mock(com.ai.infrastructure.core.AIEmbeddingService.class);
        com.ai.infrastructure.core.AICoreService aiCoreService = mock(com.ai.infrastructure.core.AICoreService.class);
        
        capabilityService = new AICapabilityService(embeddingService, aiCoreService, searchRepository);
        aspect = new AICapableAspect(capabilityService, configLoader);
        
        // Setup default mock behavior
        setupDefaultMocks();
    }

    @Test
    public void testCompleteAICapableEntityLifecycle() {
        // Given
        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Luxury Watch");
        product.setDescription("Premium Swiss watch");
        product.setPrice(5000.0);
        product.setCategory("Watches");

        // When - Create
        capabilityService.processEntityForAI(product, "create");

        // Then - Verify creation
        verify(searchRepository, times(1)).save(argThat(entity -> 
            entity.getEntityId().equals("1") && 
            entity.getEntityType().equals("product") &&
            entity.getSearchableContent().contains("Luxury Watch")
        ));

        // When - Update
        product.setName("Updated Luxury Watch");
        product.setDescription("Updated premium Swiss watch");
        capabilityService.processEntityForAI(product, "update");

        // Then - Verify update
        verify(searchRepository, times(2)).save(any(AISearchableEntity.class));

        // When - Delete
        capabilityService.removeEntityFromIndex("1", "product");

        // Then - Verify deletion
        verify(searchRepository, times(1)).deleteByEntityIdAndEntityType("1", "product");
    }

    @Test
    public void testAspectIntegrationWithAICapableAnnotation() {
        // Given
        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Test Product");
        product.setDescription("Test Description");

        // Create a proxy with the aspect
        AspectJProxyFactory factory = new AspectJProxyFactory(product);
        factory.addAspect(aspect);
        TestProduct proxy = factory.getProxy();

        // When - Call a method that should trigger AI processing
        proxy.setName("Updated Product Name");

        // Then - Verify AI processing was triggered
        verify(searchRepository, atLeastOnce()).save(any(AISearchableEntity.class));
    }

    @Test
    public void testSearchFunctionalityWithMockData() {
        // Given
        List<AISearchableEntity> mockResults = Arrays.asList(
            createMockSearchableEntity(1L, "product", "Luxury Watch", new double[]{0.1, 0.2, 0.3}),
            createMockSearchableEntity(2L, "product", "Premium Watch", new double[]{0.2, 0.3, 0.4}),
            createMockSearchableEntity(3L, "product", "Diamond Ring", new double[]{0.3, 0.4, 0.5})
        );

        when(searchRepository.findByEntityType("product")).thenReturn(mockResults);

        // When
        List<AISearchableEntity> results = capabilityService.searchEntities("luxury watch", "product");

        // Then
        assertNotNull(results);
        assertEquals(3, results.size());
        verify(searchRepository, times(1)).findByEntityType("product");
    }

    @Test
    public void testConfigurationLoadingIntegration() {
        // Given
        AIEntityConfig config = createComprehensiveConfig();
        when(configLoader.getEntityConfig("product")).thenReturn(config);

        // When
        AIEntityConfig loadedConfig = configLoader.getEntityConfig("product");

        // Then
        assertNotNull(loadedConfig);
        assertEquals("product", loadedConfig.getEntityType());
        assertEquals(3, loadedConfig.getSearchableFields().size());
        assertEquals(2, loadedConfig.getEmbeddableFields().size());
        assertEquals(1, loadedConfig.getMetadataFields().size());
        assertEquals(4, loadedConfig.getCrudOperations().size());
    }

    @Test
    public void testAsyncProcessing() {
        // Given
        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Async Product");
        product.setDescription("Async Description");

        // When - Process asynchronously
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            capabilityService.processEntity(product, "create");
        });

        // Then - Wait for completion and verify
        assertDoesNotThrow(() -> future.get());
        verify(searchRepository, times(1)).save(any(AISearchableEntity.class));
    }

    @Test
    public void testErrorHandlingInProcessing() {
        // Given
        when(searchRepository.save(any(AISearchableEntity.class)))
            .thenThrow(new RuntimeException("Database error"));

        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Error Product");
        product.setDescription("Error Description");

        // When & Then - Should handle error gracefully
        assertDoesNotThrow(() -> {
            capabilityService.processEntity(product, "create");
        });
    }

    @Test
    public void testBulkOperations() {
        // Given
        List<TestProduct> products = Arrays.asList(
            createTestProduct(1L, "Product 1", "Description 1"),
            createTestProduct(2L, "Product 2", "Description 2"),
            createTestProduct(3L, "Product 3", "Description 3")
        );

        // When
        for (TestProduct product : products) {
            capabilityService.processEntity(product, "create");
        }

        // Then
        verify(searchRepository, times(3)).save(any(AISearchableEntity.class));
    }

    @Test
    public void testMetadataProcessing() {
        // Given
        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Metadata Product");
        product.setDescription("Metadata Description");
        product.setPrice(1000.0);
        product.setCategory("Test Category");

        AIEntityConfig config = createConfigWithMetadata();
        when(configLoader.getEntityConfig("product")).thenReturn(config);

        // When
        capabilityService.processEntity(product, "create");

        // Then
        verify(searchRepository, times(1)).save(argThat(entity -> 
            entity.getMetadata() != null && 
            entity.getMetadata().contains("price") &&
            entity.getMetadata().contains("category")
        ));
    }

    @Test
    public void testSearchWithFilters() {
        // Given
        List<AISearchableEntity> mockResults = Arrays.asList(
            createMockSearchableEntity(1L, "product", "Luxury Watch", new double[]{0.1, 0.2, 0.3}),
            createMockSearchableEntity(2L, "product", "Premium Watch", new double[]{0.2, 0.3, 0.4})
        );

        when(searchRepository.findByEntityType("product")).thenReturn(mockResults);

        // When
        List<AISearchableEntity> results = capabilityService.searchEntities("watch", "product");

        // Then
        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(entity -> 
            entity.getContent().toLowerCase().contains("watch")
        ));
    }

    private void setupDefaultMocks() {
        AIEntityConfig defaultConfig = createDefaultConfig();
        when(configLoader.getEntityConfig(anyString())).thenReturn(defaultConfig);
        when(searchRepository.save(any(AISearchableEntity.class))).thenReturn(new AISearchableEntity());
        when(searchRepository.findByEntityType(anyString())).thenReturn(new ArrayList<>());
    }

    private AIEntityConfig createDefaultConfig() {
        AIEntityConfig config = new AIEntityConfig();
        config.setEntityType("product");
        config.setSearchableFields(Arrays.asList(
            createSearchableField("name", 1.0),
            createSearchableField("description", 0.8)
        ));
        config.setEmbeddableFields(Arrays.asList(
            createEmbeddableField("name", "text"),
            createEmbeddableField("description", "text")
        ));
        return config;
    }

    private AIEntityConfig createComprehensiveConfig() {
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
        config.setMetadataFields(Arrays.asList(
            createMetadataField("price", "number"),
            createMetadataField("category", "string")
        ));
        config.setCrudOperations(Arrays.asList(
            createCrudOperation("create", true, true, false),
            createCrudOperation("update", true, true, false),
            createCrudOperation("delete", false, false, true),
            createCrudOperation("read", false, false, false)
        ));
        return config;
    }

    private AIEntityConfig createConfigWithMetadata() {
        AIEntityConfig config = new AIEntityConfig();
        config.setEntityType("product");
        config.setSearchableFields(Arrays.asList(
            createSearchableField("name", 1.0),
            createSearchableField("description", 0.8)
        ));
        config.setEmbeddableFields(Arrays.asList(
            createEmbeddableField("name", "text"),
            createEmbeddableField("description", "text")
        ));
        config.setMetadataFields(Arrays.asList(
            createMetadataField("price", "number"),
            createMetadataField("category", "string")
        ));
        return config;
    }

    private AISearchableField createSearchableField(String fieldName, Double weight) {
        AISearchableField field = new AISearchableField();
        field.setFieldName(fieldName);
        field.setWeight(weight);
        return field;
    }

    private AIEmbeddableField createEmbeddableField(String fieldName, String fieldType) {
        AIEmbeddableField field = new AIEmbeddableField();
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        return field;
    }

    private AIMetadataField createMetadataField(String fieldName, String fieldType) {
        AIMetadataField field = new AIMetadataField();
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        return field;
    }

    private AICrudOperation createCrudOperation(String operation, boolean generateEmbedding, boolean indexForSearch, boolean enableAnalysis) {
        AICrudOperation crudOp = new AICrudOperation();
        crudOp.setOperation(operation);
        crudOp.setGenerateEmbedding(generateEmbedding);
        crudOp.setIndexForSearch(indexForSearch);
        crudOp.setEnableAnalysis(enableAnalysis);
        return crudOp;
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

    private TestProduct createTestProduct(Long id, String name, String description) {
        TestProduct product = new TestProduct();
        product.setId(id);
        product.setName(name);
        product.setDescription(description);
        return product;
    }

    // Test entity class
    @AICapable(entityType = "product")
    public static class TestProduct {
        private Long id;
        private String name;
        private String description;
        private Double price;
        private String category;

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
    }
}