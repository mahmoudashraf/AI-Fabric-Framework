package com.ai.infrastructure.it;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.entity.TestUser;
import com.ai.infrastructure.it.entity.TestArticle;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.repository.TestUserRepository;
import com.ai.infrastructure.it.repository.TestArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real API Integration Tests for AI Infrastructure Module
 * 
 * These tests actually call OpenAI APIs and require valid API keys.
 * They test the complete AI infrastructure functionality with real data.
 * 
 * To run these tests, set the environment variable:
 * export OPENAI_API_KEY=your_actual_openai_api_key
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
@Transactional
public class RealAPIIntegrationTest {

    @Autowired
    private AIEmbeddingService embeddingService;

    @Autowired
    private AICoreService aiCoreService;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestArticleRepository articleRepository;

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
        AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
        List<Double> embeddings = response.getEmbedding();

        // Then
        assertNotNull(embeddings, "Embeddings should not be null");
        assertFalse(embeddings.isEmpty(), "Embeddings should not be empty");
        assertEquals(1536, embeddings.size(), "Should generate 1536-dimensional embeddings");

        // Verify embeddings are reasonable (not all zeros or same value)
        boolean hasVariation = embeddings.stream().anyMatch(d -> d != 0.0);
        assertTrue(hasVariation, "Embeddings should have variation, not be all zeros");

        System.out.println("✅ Generated embeddings for: " + testText);
        System.out.println("First 5 dimensions: " + embeddings.subList(0, 5));
    }

    @Test
    public void testRealAIContentGeneration() {
        // Given
        String prompt = "Analyze this product: Luxury Swiss watch with diamond bezel, automatic movement, water resistant to 100m. Price: $5,000. Generate a marketing description.";
        AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt(prompt)
                .model("gpt-3.5-turbo")
                .maxTokens(200)
                .temperature(0.7)
                .build();

        // When
        AIGenerationResponse response = aiCoreService.generateContent(request);
        String content = response.getContent();

        // Then
        assertNotNull(content, "AI response should not be null");
        assertFalse(content.trim().isEmpty(), "AI response should not be empty");
        assertTrue(content.length() > 10, "AI response should be substantial");

        System.out.println("✅ AI Analysis: " + content);
    }

    @Test
    public void testRealProductProcessing() {
        // Given
        TestProduct product = TestProduct.builder()
                .name("Luxury Swiss Watch")
                .description("Premium Swiss automatic watch with diamond bezel and sapphire crystal")
                .category("Watches")
                .brand("SwissLux")
                .price(new BigDecimal("5000.00"))
                .sku("SWL-001")
                .stockQuantity(10)
                .active(true)
                .build();

        // When
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        // Then
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityTypeAndEntityId("test-product", product.getId().toString());
        assertFalse(searchableEntities.isEmpty(), "Should create searchable entity");

        AISearchableEntity searchableEntity = searchableEntities.get(0);
        assertNotNull(searchableEntity.getEmbeddings(), "Should have embeddings");
        assertFalse(searchableEntity.getEmbeddings().isEmpty(), "Embeddings should not be empty");
        assertNotNull(searchableEntity.getSearchableContent(), "Should have searchable content");
        assertTrue(searchableEntity.getSearchableContent().contains("Luxury Swiss Watch"), "Should contain product name");

        System.out.println("✅ Processed product: " + product.getName());
        System.out.println("Searchable content: " + searchableEntity.getSearchableContent());
    }

    @Test
    public void testRealUserProcessing() {
        // Given
        TestUser user = TestUser.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .bio("Software engineer with 10 years of experience in AI and machine learning")
                .age(35)
                .location("San Francisco, CA")
                .active(true)
                .build();

        // When
        user = userRepository.save(user);
        capabilityService.processEntityForAI(user, "test-user");

        // Then
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityTypeAndEntityId("test-user", user.getId().toString());
        assertFalse(searchableEntities.isEmpty(), "Should create searchable entity");

        AISearchableEntity searchableEntity = searchableEntities.get(0);
        assertNotNull(searchableEntity.getEmbeddings(), "Should have embeddings");
        assertNotNull(searchableEntity.getSearchableContent(), "Should have searchable content");
        assertTrue(searchableEntity.getSearchableContent().contains("John Doe"), "Should contain user name");

        System.out.println("✅ Processed user: " + user.getFullName());
        System.out.println("Searchable content: " + searchableEntity.getSearchableContent());
    }

    @Test
    public void testRealArticleProcessing() {
        // Given
        TestArticle article = TestArticle.builder()
                .title("The Future of AI in Software Development")
                .content("Artificial Intelligence is revolutionizing software development through automated code generation, intelligent testing, and predictive analytics...")
                .summary("Exploring how AI is transforming software development practices")
                .author("Jane Smith")
                .tags("AI,Software Development,Technology")
                .readTime(5)
                .published(true)
                .build();

        // When
        article = articleRepository.save(article);
        capabilityService.processEntityForAI(article, "test-article");

        // Then
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityTypeAndEntityId("test-article", article.getId().toString());
        assertFalse(searchableEntities.isEmpty(), "Should create searchable entity");

        AISearchableEntity searchableEntity = searchableEntities.get(0);
        assertNotNull(searchableEntity.getEmbeddings(), "Should have embeddings");
        assertNotNull(searchableEntity.getSearchableContent(), "Should have searchable content");
        assertTrue(searchableEntity.getSearchableContent().contains("Future of AI"), "Should contain article title");

        System.out.println("✅ Processed article: " + article.getTitle());
        System.out.println("Searchable content: " + searchableEntity.getSearchableContent());
    }

    @Test
    public void testRealSearchFunctionality() {
        // Given - Create and process multiple entities
        TestProduct product1 = TestProduct.builder()
                .name("Luxury Watch")
                .description("Swiss automatic watch with diamond bezel")
                .category("Watches")
                .price(new BigDecimal("5000.00"))
                .active(true)
                .build();

        TestProduct product2 = TestProduct.builder()
                .name("Smart Watch")
                .description("Digital watch with fitness tracking and notifications")
                .category("Watches")
                .price(new BigDecimal("300.00"))
                .active(true)
                .build();

        product1 = productRepository.save(product1);
        product2 = productRepository.save(product2);

        capabilityService.processEntityForAI(product1, "test-product");
        capabilityService.processEntityForAI(product2, "test-product");

        // When - Search for luxury items
        String searchQuery = "luxury diamond watch";
        List<AISearchableEntity> results = searchRepository.findBySearchableContentContainingIgnoreCase(searchQuery);

        // Then
        assertFalse(results.isEmpty(), "Should find matching results");
        
        // Verify results contain relevant content
        boolean foundLuxuryWatch = results.stream()
                .anyMatch(entity -> entity.getSearchableContent().toLowerCase().contains("luxury"));
        assertTrue(foundLuxuryWatch, "Should find luxury watch in results");

        System.out.println("✅ Search results for '" + searchQuery + "': " + results.size() + " found");
        results.forEach(result -> 
            System.out.println("  - " + result.getSearchableContent().substring(0, Math.min(100, result.getSearchableContent().length())) + "...")
        );
    }

    @Test
    public void testRealAIAnalysis() {
        // Given
        TestProduct product = TestProduct.builder()
                .name("Eco-Friendly Water Bottle")
                .description("BPA-free stainless steel water bottle with double-wall insulation")
                .category("Kitchen")
                .brand("EcoLife")
                .price(new BigDecimal("25.99"))
                .active(true)
                .build();

        // When
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        // Then
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityTypeAndEntityId("test-product", product.getId().toString());
        assertFalse(searchableEntities.isEmpty(), "Should create searchable entity");

        AISearchableEntity searchableEntity = searchableEntities.get(0);
        assertNotNull(searchableEntity.getAiAnalysis(), "Should have AI analysis");
        assertFalse(searchableEntity.getAiAnalysis().trim().isEmpty(), "AI analysis should not be empty");

        System.out.println("✅ AI Analysis for " + product.getName() + ":");
        System.out.println(searchableEntity.getAiAnalysis());
    }

    @Test
    public void testRealValidation() {
        // Given
        TestUser user = TestUser.builder()
                .firstName("") // Invalid: empty first name
                .lastName("Doe")
                .email("invalid-email") // Invalid: malformed email
                .bio("Valid bio content")
                .age(-5) // Invalid: negative age
                .active(true)
                .build();

        // When
        user = userRepository.save(user);
        capabilityService.processEntityForAI(user, "test-user");

        // Then
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityTypeAndEntityId("test-user", user.getId().toString());
        assertFalse(searchableEntities.isEmpty(), "Should create searchable entity even with invalid data");

        AISearchableEntity searchableEntity = searchableEntities.get(0);
        assertNotNull(searchableEntity.getSearchableContent(), "Should have searchable content");

        System.out.println("✅ Processed user with validation issues: " + user.getEmail());
        System.out.println("Searchable content: " + searchableEntity.getSearchableContent());
    }

    @Test
    public void testRealBatchProcessing() {
        // Given - Create multiple entities
        List<TestProduct> products = List.of(
            TestProduct.builder().name("Product 1").description("Description 1").category("Cat1").active(true).build(),
            TestProduct.builder().name("Product 2").description("Description 2").category("Cat2").active(true).build(),
            TestProduct.builder().name("Product 3").description("Description 3").category("Cat1").active(true).build()
        );

        // When
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Then
        List<AISearchableEntity> allSearchableEntities = searchRepository.findByEntityType("test-product");
        assertEquals(products.size(), allSearchableEntities.size(), "Should process all products");

        System.out.println("✅ Batch processed " + products.size() + " products");
        System.out.println("Created " + allSearchableEntities.size() + " searchable entities");
    }
}