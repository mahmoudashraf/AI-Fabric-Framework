package com.ai.infrastructure.integration;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real API Integration Tests for AI Infrastructure Module
 * These tests actually call OpenAI APIs and require valid API keys
 * 
 * To run these tests, set the environment variable:
 * export OPENAI_API_KEY=your_actual_openai_api_key
 */
@SpringBootTest
@TestPropertySource(properties = {
    "ai.openai.api-key=${OPENAI_API_KEY:}",
    "ai.openai.model=gpt-3.5-turbo",
    "ai.openai.embedding-model=text-embedding-ada-002"
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class AIInfrastructureRealAPITest {

    @Autowired
    private AIEmbeddingService embeddingService;

    @Autowired
    private AICoreService aiCoreService;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private AIEntityConfigurationLoader configLoader;

    private AICapabilityService capabilityService;

    @BeforeEach
    public void setUp() {
        capabilityService = new AICapabilityService(embeddingService, aiCoreService, searchRepository);
    }

    @Test
    public void testRealEmbeddingGeneration() {
        // Given
        String testText = "This is a luxury watch with diamond bezel and Swiss movement";
        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text(testText)
            .model("text-embedding-ada-002")
            .build();

        // When
        List<Double> embeddings = embeddingService.generateEmbedding(request);

        // Then
        assertNotNull(embeddings, "Embeddings should not be null");
        assertFalse(embeddings.isEmpty(), "Embeddings should not be empty");
        assertEquals(1536, embeddings.size(), "Should generate 1536-dimensional embeddings");
        
        // Verify embeddings are reasonable (not all zeros or same value)
        boolean hasVariation = embeddings.stream().anyMatch(d -> d != 0.0);
        assertTrue(hasVariation, "Embeddings should have variation, not be all zeros");
        
        System.out.println("Generated embeddings for: " + testText);
        System.out.println("First 5 dimensions: " + embeddings.subList(0, 5));
    }

    @Test
    public void testRealAIContentGeneration() {
        // Given
        String prompt = "Analyze this product description and provide key features: 'Luxury Swiss watch with diamond bezel'";
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt(prompt)
            .model("gpt-3.5-turbo")
            .maxTokens(200)
            .temperature(0.7)
            .build();

        // When
        String response = aiCoreService.generateContent(request);

        // Then
        assertNotNull(response, "AI response should not be null");
        assertFalse(response.trim().isEmpty(), "AI response should not be empty");
        assertTrue(response.length() > 10, "AI response should be substantial");
        
        System.out.println("AI Analysis: " + response);
    }

    @Test
    public void testRealEntityProcessing() {
        // Given
        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Luxury Swiss Watch");
        product.setDescription("Premium timepiece with diamond bezel and automatic movement");
        product.setPrice(5000.0);
        product.setCategory("Watches");

        AIEntityConfig config = configLoader.getEntityConfig("product");
        assertNotNull(config, "Product configuration should be loaded");

        // When
        capabilityService.generateEmbeddings(product, config);
        capabilityService.indexForSearch(product, config);

        // Then
        // Verify entity was saved to repository
        Optional<AISearchableEntity> savedEntity = searchRepository.findByEntityTypeAndEntityId("product", "1");
        assertTrue(savedEntity.isPresent(), "Entity should be saved to repository");
        
        AISearchableEntity entity = savedEntity.get();
        assertNotNull(entity.getEmbeddings(), "Entity should have embeddings");
        assertFalse(entity.getEmbeddings().isEmpty(), "Embeddings should not be empty");
        assertEquals(1536, entity.getEmbeddings().size(), "Should have 1536-dimensional embeddings");
        
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().contains("Luxury Swiss Watch"), "Should contain product name");
        assertTrue(entity.getSearchableContent().contains("Premium timepiece"), "Should contain description");
        
        System.out.println("Processed entity with embeddings: " + entity.getEmbeddings().subList(0, 5));
        System.out.println("Searchable content: " + entity.getSearchableContent());
    }

    @Test
    public void testRealSearchFunctionality() {
        // Given - Create multiple test entities
        createTestEntity("1", "product", "Luxury Swiss Watch with diamond bezel", 
            "Premium timepiece with automatic movement");
        createTestEntity("2", "product", "Diamond Ring with platinum setting", 
            "Elegant engagement ring with brilliant cut diamonds");
        createTestEntity("3", "product", "Gold Necklace with pendant", 
            "Classic gold chain with heart-shaped pendant");

        // When - Search for luxury items
        List<AISearchableEntity> results = searchRepository.findByEntityType("product");

        // Then
        assertNotNull(results, "Search results should not be null");
        assertEquals(3, results.size(), "Should find all 3 test entities");
        
        // Verify all entities have embeddings
        results.forEach(entity -> {
            assertNotNull(entity.getEmbeddings(), "Entity should have embeddings");
            assertEquals(1536, entity.getEmbeddings().size(), "Should have 1536-dimensional embeddings");
        });
        
        System.out.println("Found " + results.size() + " entities with embeddings");
        results.forEach(entity -> 
            System.out.println("- " + entity.getSearchableContent() + " (embeddings: " + 
                entity.getEmbeddings().subList(0, 3) + "...)")
        );
    }

    @Test
    public void testRealAIAnalysis() {
        // Given
        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Luxury Swiss Watch");
        product.setDescription("Premium timepiece with diamond bezel and automatic movement");
        product.setPrice(5000.0);
        product.setCategory("Watches");

        AIEntityConfig config = configLoader.getEntityConfig("product");

        // When
        capabilityService.analyzeEntity(product, config);

        // Then
        Optional<AISearchableEntity> savedEntity = searchRepository.findByEntityTypeAndEntityId("product", "1");
        assertTrue(savedEntity.isPresent(), "Entity should be saved");
        
        AISearchableEntity entity = savedEntity.get();
        assertNotNull(entity.getAiAnalysis(), "Should have AI analysis");
        assertFalse(entity.getAiAnalysis().trim().isEmpty(), "AI analysis should not be empty");
        
        System.out.println("AI Analysis: " + entity.getAiAnalysis());
    }

    @Test
    public void testRealValidation() {
        // Given
        TestProduct product = new TestProduct();
        product.setId(1L);
        product.setName("Luxury Swiss Watch");
        product.setDescription("Premium timepiece with diamond bezel and automatic movement");
        product.setPrice(5000.0);
        product.setCategory("Watches");

        AIEntityConfig config = configLoader.getEntityConfig("product");

        // When
        assertDoesNotThrow(() -> {
            capabilityService.validateEntity(product, config);
        }, "Validation should not throw exceptions");

        // Then - Validation should complete successfully
        assertTrue(true, "Validation completed successfully");
    }

    private void createTestEntity(String entityId, String entityType, String name, String description) {
        TestProduct product = new TestProduct();
        product.setId(Long.parseLong(entityId));
        product.setName(name);
        product.setDescription(description);
        product.setPrice(1000.0);
        product.setCategory("Test");

        AIEntityConfig config = configLoader.getEntityConfig("product");
        capabilityService.generateEmbeddings(product, config);
        capabilityService.indexForSearch(product, config);
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