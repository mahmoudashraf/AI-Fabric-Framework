package com.ai.infrastructure.it;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Workflow Tests for AI Infrastructure Module
 * 
 * These tests simulate complete user workflows and business scenarios
 * to ensure the AI infrastructure works correctly in real-world usage.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Transactional
public class EndToEndWorkflowTest {

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestArticleRepository articleRepository;

    @BeforeEach
    public void setUp() {
        // Clean up before each test
        searchRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        articleRepository.deleteAll();
    }

    @Test
    public void testProductCatalogWorkflow() {
        // Scenario: E-commerce product catalog with AI-powered search and recommendations
        
        // Given - Create a product catalog
        List<TestProduct> products = List.of(
            TestProduct.builder()
                .name("Luxury Swiss Watch")
                .description("Premium automatic watch with diamond bezel")
                .category("Watches")
                .brand("SwissLux")
                .price(new BigDecimal("5000.00"))
                .sku("SWL-001")
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Smart Fitness Watch")
                .description("Digital watch with heart rate monitoring and GPS")
                .category("Watches")
                .brand("TechFit")
                .price(new BigDecimal("299.99"))
                .sku("TF-001")
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Classic Leather Watch")
                .description("Traditional leather strap watch with quartz movement")
                .category("Watches")
                .brand("ClassicTime")
                .price(new BigDecimal("150.00"))
                .sku("CT-001")
                .active(true)
                .build()
        );

        // When - Process products for AI
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Then - Verify AI processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-product");
        assertEquals(3, searchableEntities.size(), "Should process all products");

        // Verify each product has AI data
        for (AISearchableEntity entity : searchableEntities) {
            assertNotNull(entity.getEmbeddings(), "Should have embeddings");
            assertFalse(entity.getEmbeddings().isEmpty(), "Embeddings should not be empty");
            assertNotNull(entity.getSearchableContent(), "Should have searchable content");
            assertTrue(entity.getSearchableContent().length() > 0, "Searchable content should not be empty");
        }

        System.out.println("✅ Product catalog workflow completed successfully");
        System.out.println("Processed " + products.size() + " products with AI capabilities");
    }

    @Test
    public void testUserProfileWorkflow() {
        // Scenario: User registration and profile management with AI analysis
        
        // Given - Create user profiles
        List<TestUser> users = List.of(
            TestUser.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .bio("Software engineer passionate about AI and machine learning")
                .age(30)
                .location("San Francisco, CA")
                .active(true)
                .build(),
            TestUser.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .bio("Data scientist with expertise in deep learning and NLP")
                .age(28)
                .location("New York, NY")
                .active(true)
                .build()
        );

        // When - Process users for AI
        users = userRepository.saveAll(users);
        for (TestUser user : users) {
            capabilityService.processEntityForAI(user, "test-user");
        }

        // Then - Verify AI processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-user");
        assertEquals(2, searchableEntities.size(), "Should process all users");

        // Verify AI analysis for each user
        for (AISearchableEntity entity : searchableEntities) {
            assertNotNull(entity.getEmbeddings(), "Should have embeddings");
            assertNotNull(entity.getSearchableContent(), "Should have searchable content");
            assertTrue(entity.getSearchableContent().contains("engineer") || 
                      entity.getSearchableContent().contains("scientist"), 
                      "Should contain professional information");
        }

        System.out.println("✅ User profile workflow completed successfully");
        System.out.println("Processed " + users.size() + " user profiles with AI analysis");
    }

    @Test
    public void testContentManagementWorkflow() {
        // Scenario: Content management system with AI-powered content analysis
        
        // Given - Create articles
        List<TestArticle> articles = List.of(
            TestArticle.builder()
                .title("The Future of Artificial Intelligence")
                .content("Artificial Intelligence is rapidly evolving and transforming industries...")
                .summary("Exploring AI trends and future possibilities")
                .author("Dr. AI Expert")
                .tags("AI,Technology,Future")
                .readTime(10)
                .published(true)
                .build(),
            TestArticle.builder()
                .title("Machine Learning Best Practices")
                .content("Here are the essential best practices for machine learning projects...")
                .summary("A comprehensive guide to ML best practices")
                .author("ML Engineer")
                .tags("Machine Learning,Best Practices,Guide")
                .readTime(15)
                .published(true)
                .build()
        );

        // When - Process articles for AI
        articles = articleRepository.saveAll(articles);
        for (TestArticle article : articles) {
            capabilityService.processEntityForAI(article, "test-article");
        }

        // Then - Verify AI processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-article");
        assertEquals(2, searchableEntities.size(), "Should process all articles");

        // Verify content analysis
        for (AISearchableEntity entity : searchableEntities) {
            assertNotNull(entity.getEmbeddings(), "Should have embeddings");
            assertNotNull(entity.getSearchableContent(), "Should have searchable content");
            assertTrue(entity.getSearchableContent().length() > 50, "Should have substantial content");
        }

        System.out.println("✅ Content management workflow completed successfully");
        System.out.println("Processed " + articles.size() + " articles with AI content analysis");
    }

    @Test
    public void testSearchAndDiscoveryWorkflow() {
        // Scenario: AI-powered search and discovery system
        
        // Given - Create diverse content
        TestProduct product = TestProduct.builder()
            .name("AI-Powered Smart Home Device")
            .description("Intelligent home automation device with machine learning capabilities")
            .category("Smart Home")
            .price(new BigDecimal("199.99"))
            .active(true)
            .build();

        TestArticle article = TestArticle.builder()
            .title("Smart Home AI Integration Guide")
            .content("Learn how to integrate AI into your smart home setup...")
            .summary("Complete guide to AI-powered smart homes")
            .author("Smart Home Expert")
            .tags("AI,Smart Home,Integration")
            .published(true)
            .build();

        // When - Process and search
        product = productRepository.save(product);
        article = articleRepository.save(article);
        
        capabilityService.processEntityForAI(product, "test-product");
        capabilityService.processEntityForAI(article, "test-article");

        // Then - Test search functionality
        List<AISearchableEntity> allEntities = searchRepository.findAll();
        assertEquals(2, allEntities.size(), "Should have both entities");

        // Search for AI-related content
        List<AISearchableEntity> aiResults = searchRepository.findBySearchableContentContainingIgnoreCase("AI");
        assertFalse(aiResults.isEmpty(), "Should find AI-related content");

        // Search for smart home content
        List<AISearchableEntity> smartHomeResults = searchRepository.findBySearchableContentContainingIgnoreCase("smart home");
        assertFalse(smartHomeResults.isEmpty(), "Should find smart home content");

        System.out.println("✅ Search and discovery workflow completed successfully");
        System.out.println("Found " + aiResults.size() + " AI-related results");
        System.out.println("Found " + smartHomeResults.size() + " smart home results");
    }

    @Test
    public void testDataCleanupWorkflow() {
        // Scenario: Data cleanup and maintenance workflow
        
        // Given - Create and process entities
        TestProduct product = TestProduct.builder()
            .name("Temporary Product")
            .description("This product will be deleted")
            .category("Test")
            .active(true)
            .build();

        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        // Verify entity exists
        List<AISearchableEntity> entities = searchRepository.findByEntityTypeAndEntityId("test-product", product.getId().toString());
        assertEquals(1, entities.size(), "Should have one entity");

        // When - Delete the product
        productRepository.delete(product);
        capabilityService.removeEntityFromIndex(product.getId().toString(), "test-product");

        // Then - Verify cleanup
        List<AISearchableEntity> remainingEntities = searchRepository.findByEntityTypeAndEntityId("test-product", product.getId().toString());
        assertTrue(remainingEntities.isEmpty(), "Should remove entity from index");

        System.out.println("✅ Data cleanup workflow completed successfully");
        System.out.println("Entity removed from AI index");
    }

    @Test
    public void testBatchProcessingWorkflow() {
        // Scenario: Batch processing of multiple entities
        
        // Given - Create a large batch of entities
        List<TestProduct> products = List.of(
            TestProduct.builder().name("Product 1").description("Description 1").category("Cat1").active(true).build(),
            TestProduct.builder().name("Product 2").description("Description 2").category("Cat2").active(true).build(),
            TestProduct.builder().name("Product 3").description("Description 3").category("Cat1").active(true).build(),
            TestProduct.builder().name("Product 4").description("Description 4").category("Cat3").active(true).build(),
            TestProduct.builder().name("Product 5").description("Description 5").category("Cat2").active(true).build()
        );

        // When - Process batch
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Then - Verify batch processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-product");
        assertEquals(5, searchableEntities.size(), "Should process all products in batch");

        // Verify all entities have AI data
        for (AISearchableEntity entity : searchableEntities) {
            assertNotNull(entity.getEmbeddings(), "Should have embeddings");
            assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        }

        System.out.println("✅ Batch processing workflow completed successfully");
        System.out.println("Processed " + products.size() + " products in batch");
    }
}