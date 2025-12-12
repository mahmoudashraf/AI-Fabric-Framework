package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.entity.TestUser;
import com.ai.infrastructure.it.entity.TestArticle;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.repository.TestUserRepository;
import com.ai.infrastructure.it.repository.TestArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock Integration Test for AI Infrastructure Module
 * 
 * Tests all core AI functions using mocked services that don't require API keys.
 * This allows for comprehensive testing without external dependencies.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("mock-test")
@TestPropertySource(properties = {
    "ai.vector-db.lucene.index-path=./data/test-lucene-index/mock",
    "ai-infrastructure.storage.strategy=SINGLE_TABLE"
})
@Transactional
public class MockIntegrationTest {

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

    @Autowired
    private VectorManagementService vectorManagementService;

    @BeforeEach
    public void setUp() {
        // Clean up before each test
        searchRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        articleRepository.deleteAll();
    }

    @Test
    public void testMockEmbeddingGeneration() {
        System.out.println("🧪 Testing Mock Embedding Generation...");
        
        // Given - Create a product with rich content
        TestProduct product = TestProduct.builder()
            .name("AI-Powered Smart Watch")
            .description("Advanced smartwatch with AI-powered health monitoring, sleep tracking, and personalized recommendations")
            .category("Wearables")
            .brand("TechCorp")
            .price(new BigDecimal("299.99"))
            .sku("SW-AI-001")
            .stockQuantity(50)
            .active(true)
            .build();

        // When - Save and process the product
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        // Then - Verify AI processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-product");
        assertEquals(1, searchableEntities.size(), "Should process one product");

        AISearchableEntity entity = searchableEntities.get(0);
        assertNotNull(entity.getVectorId(), "Should have vector ID");
        assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");
        
        // Verify vector exists in vector database
        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), 
                  "Vector should exist in vector database");
        assertTrue(entity.getVectorId().length() >= 30, "Should have substantial embeddings");
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().contains("AI-Powered"), "Should contain product name");
        assertTrue(entity.getSearchableContent().contains("smartwatch"), "Should contain description");

        System.out.println("✅ Mock Embedding Generation Test Passed");
        System.out.println("   - Vector ID: " + entity.getVectorId().length());
        System.out.println("   - Searchable content length: " + entity.getSearchableContent().length());
    }

    @Test
    public void testMockUserProcessing() {
        System.out.println("🧪 Testing Mock User Processing...");
        
        // Given - Create a user with profile information
        TestUser user = TestUser.builder()
            .firstName("John")
            .lastName("Doe")
            .email("john.doe@example.com")
            .bio("Software engineer passionate about AI and machine learning. Love working on innovative projects.")
            .age(28)
            .location("San Francisco, CA")
            .phoneNumber("+1-555-0123")
            .dateOfBirth(LocalDate.of(1995, 6, 15))
            .active(true)
            .build();

        // When - Save and process the user
        user = userRepository.save(user);
        capabilityService.processEntityForAI(user, "test-user");

        // Then - Verify AI processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-user");
        assertEquals(1, searchableEntities.size(), "Should process one user");

        AISearchableEntity entity = searchableEntities.get(0);
        assertNotNull(entity.getVectorId(), "Should have vector ID");
        assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");
        
        // Verify vector exists in vector database
        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), 
                  "Vector should exist in vector database");
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().contains("John Doe"), "Should contain user name");
        assertTrue(entity.getSearchableContent().contains("AI and machine learning"), "Should contain bio content");

        System.out.println("✅ Mock User Processing Test Passed");
        System.out.println("   - Vector ID: " + entity.getVectorId().length());
        System.out.println("   - Searchable content length: " + entity.getSearchableContent().length());
    }

    @Test
    public void testMockArticleProcessing() {
        System.out.println("🧪 Testing Mock Article Processing...");
        
        // Given - Create an article with rich content
        TestArticle article = TestArticle.builder()
            .title("The Future of Artificial Intelligence in Healthcare")
            .content("Artificial Intelligence is revolutionizing healthcare through advanced diagnostics, personalized treatment plans, and predictive analytics. This comprehensive guide explores the latest developments in AI-powered medical technologies.")
            .summary("AI is transforming healthcare with advanced diagnostics and personalized treatments")
            .author("Dr. Sarah Johnson")
            .tags("AI, Healthcare, Technology, Innovation")
            .published(true)
            .publishDate(LocalDateTime.now())
            .readTime(8)
            .viewCount(1250)
            .build();

        // When - Save and process the article
        article = articleRepository.save(article);
        capabilityService.processEntityForAI(article, "test-article");

        // Then - Verify AI processing
        List<AISearchableEntity> searchableEntities = searchRepository.findByEntityType("test-article");
        assertEquals(1, searchableEntities.size(), "Should process one article");

        AISearchableEntity entity = searchableEntities.get(0);
        assertNotNull(entity.getVectorId(), "Should have vector ID");
        assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");
        
        // Verify vector exists in vector database
        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), 
                  "Vector should exist in vector database");
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().contains("Artificial Intelligence"), "Should contain title");
        assertTrue(entity.getSearchableContent().contains("healthcare"), "Should contain content");

        System.out.println("✅ Mock Article Processing Test Passed");
        System.out.println("   - Vector ID: " + entity.getVectorId().length());
        System.out.println("   - Searchable content length: " + entity.getSearchableContent().length());
    }

    @Test
    public void testMockSearchFunctionality() {
        System.out.println("🧪 Testing Mock Search Functionality...");
        
        // Given - Create multiple entities with different content
        List<TestProduct> products = List.of(
            TestProduct.builder()
                .name("AI-Powered Laptop")
                .description("High-performance laptop with AI acceleration for machine learning workloads")
                .category("Computers")
                .price(new BigDecimal("1999.99"))
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Regular Mouse")
                .description("Standard computer mouse for everyday use")
                .category("Accessories")
                .price(new BigDecimal("29.99"))
                .active(true)
                .build()
        );

        List<TestUser> users = List.of(
            TestUser.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .bio("Data scientist specializing in AI and machine learning")
                .active(true)
                .build(),
            TestUser.builder()
                .firstName("Bob")
                .lastName("Johnson")
                .email("bob@example.com")
                .bio("Marketing professional with focus on digital campaigns")
                .active(true)
                .build()
        );

        // When - Process all entities
        products = productRepository.saveAll(products);
        users = userRepository.saveAll(users);
        
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }
        for (TestUser user : users) {
            capabilityService.processEntityForAI(user, "test-user");
        }

        // Then - Test various search scenarios
        List<AISearchableEntity> allEntities = searchRepository.findByEntityType("test-product");
        assertEquals(2, allEntities.size(), "Should have two products");

        // Search for AI-related content
        List<AISearchableEntity> aiResults = searchRepository.findBySearchableContentContainingIgnoreCase("AI");
        assertFalse(aiResults.isEmpty(), "Should find AI-related content");
        assertTrue(aiResults.size() >= 2, "Should find multiple AI-related results");

        // Search for specific terms
        List<AISearchableEntity> laptopResults = searchRepository.findBySearchableContentContainingIgnoreCase("laptop");
        assertFalse(laptopResults.isEmpty(), "Should find laptop-related content");

        List<AISearchableEntity> dataResults = searchRepository.findBySearchableContentContainingIgnoreCase("data scientist");
        assertFalse(dataResults.isEmpty(), "Should find data scientist content");

        System.out.println("✅ Mock Search Functionality Test Passed");
        System.out.println("   - Total entities: " + searchRepository.count());
        System.out.println("   - AI-related results: " + aiResults.size());
        System.out.println("   - Laptop results: " + laptopResults.size());
        System.out.println("   - Data scientist results: " + dataResults.size());
    }

    @Test
    public void testMockEntityRemoval() {
        System.out.println("🧪 Testing Mock Entity Removal...");
        
        // Given - Create and process entities
        TestProduct product = TestProduct.builder()
            .name("Product to Remove")
            .description("This product will be removed from the index")
            .category("Test")
            .price(new BigDecimal("99.99"))
            .active(true)
            .build();

        TestUser user = TestUser.builder()
            .firstName("User")
            .lastName("ToRemove")
            .email("remove@example.com")
            .bio("This user will be removed")
            .active(true)
            .build();

        product = productRepository.save(product);
        user = userRepository.save(user);
        
        capabilityService.processEntityForAI(product, "test-product");
        capabilityService.processEntityForAI(user, "test-user");

        // Verify entities exist
        assertEquals(2, searchRepository.count(), "Should have two entities before removal");

        // When - Remove entities
        productRepository.delete(product);
        userRepository.delete(user);
        
        capabilityService.removeEntityFromIndex(product.getId().toString(), "test-product");
        capabilityService.removeEntityFromIndex(user.getId().toString(), "test-user");

        // Then - Verify removal
        assertEquals(0, searchRepository.count(), "Should remove all entities from index");

        System.out.println("✅ Mock Entity Removal Test Passed");
        System.out.println("   - Entities before removal: 2");
        System.out.println("   - Entities after removal: " + searchRepository.count());
    }

    @Test
    public void testMockMultiEntityWorkflow() {
        System.out.println("🧪 Testing Mock Multi-Entity Workflow...");
        
        // Given - Create a complex scenario with multiple entity types
        TestUser author = TestUser.builder()
            .firstName("Dr. Jane")
            .lastName("Wilson")
            .email("jane.wilson@example.com")
            .bio("AI researcher and technology writer with 10+ years experience")
            .active(true)
            .build();

        TestArticle article = TestArticle.builder()
            .title("Machine Learning in E-commerce: A Complete Guide")
            .content("This comprehensive guide covers how machine learning is transforming e-commerce through recommendation systems, fraud detection, and customer analytics.")
            .summary("ML is revolutionizing e-commerce with smart recommendations and analytics")
            .author("Dr. Jane Wilson")
            .tags("Machine Learning, E-commerce, AI, Analytics")
            .published(true)
            .publishDate(LocalDateTime.now())
            .readTime(12)
            .viewCount(5000)
            .build();

        TestProduct featuredProduct = TestProduct.builder()
            .name("ML-Powered Recommendation Engine")
            .description("Advanced recommendation system using machine learning algorithms for personalized product suggestions")
            .category("Software")
            .brand("TechSolutions")
            .price(new BigDecimal("9999.99"))
            .active(true)
            .build();

        // When - Process all entities
        author = userRepository.save(author);
        article = articleRepository.save(article);
        featuredProduct = productRepository.save(featuredProduct);

        capabilityService.processEntityForAI(author, "test-user");
        capabilityService.processEntityForAI(article, "test-article");
        capabilityService.processEntityForAI(featuredProduct, "test-product");

        // Then - Verify comprehensive search capabilities
        List<AISearchableEntity> allEntities = searchRepository.findAll();
        assertEquals(3, allEntities.size(), "Should process all three entities");

        // Search for machine learning content across all entities
        List<AISearchableEntity> mlResults = searchRepository.findBySearchableContentContainingIgnoreCase("machine learning");
        assertTrue(mlResults.size() >= 2, "Should find ML content in multiple entities");

        // Search for e-commerce content
        List<AISearchableEntity> ecommerceResults = searchRepository.findBySearchableContentContainingIgnoreCase("e-commerce");
        assertFalse(ecommerceResults.isEmpty(), "Should find e-commerce content");

        // Search for recommendation content
        List<AISearchableEntity> recommendationResults = searchRepository.findBySearchableContentContainingIgnoreCase("recommendation");
        assertFalse(recommendationResults.isEmpty(), "Should find recommendation content");

        System.out.println("✅ Mock Multi-Entity Workflow Test Passed");
        System.out.println("   - Total entities processed: " + allEntities.size());
        System.out.println("   - ML-related results: " + mlResults.size());
        System.out.println("   - E-commerce results: " + ecommerceResults.size());
        System.out.println("   - Recommendation results: " + recommendationResults.size());
    }

    @Test
    public void testMockAIAnalysisAndMetadata() {
        System.out.println("🧪 Testing Mock AI Analysis and Metadata...");
        
        // Given - Create a product with rich metadata
        TestProduct product = TestProduct.builder()
            .name("Advanced AI Camera")
            .description("Professional camera with AI-powered object recognition, scene optimization, and automatic photo enhancement")
            .category("Photography")
            .brand("PhotoTech")
            .price(new BigDecimal("1299.99"))
            .sku("CAM-AI-PRO")
            .stockQuantity(25)
            .active(true)
            .build();

        // When - Process the product
        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        // Then - Verify AI analysis and metadata
        List<AISearchableEntity> entities = searchRepository.findByEntityType("test-product");
        assertEquals(1, entities.size(), "Should process one product");

        AISearchableEntity entity = entities.get(0);
        
        // Verify embeddings
        assertNotNull(entity.getVectorId(), "Should have vector ID");
        assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");
        
        // Verify vector exists in vector database
        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), 
                  "Vector should exist in vector database");
        assertTrue(entity.getVectorId().length() >= 30, "Should have substantial embeddings");

        // Verify searchable content
        assertNotNull(entity.getSearchableContent(), "Should have searchable content");
        assertTrue(entity.getSearchableContent().length() > 50, "Should have substantial searchable content");
        assertTrue(entity.getSearchableContent().contains("AI Camera"), "Should contain product name");
        assertTrue(entity.getSearchableContent().contains("object recognition"), "Should contain key features");

        // Verify metadata
        assertNotNull(entity.getMetadata(), "Should have metadata");
        assertTrue(entity.getMetadata().contains("category"), "Should have category metadata");
        assertTrue(entity.getMetadata().contains("brand"), "Should have brand metadata");
        assertTrue(entity.getMetadata().contains("price"), "Should have price metadata");

        // Verify timestamps
        assertNotNull(entity.getCreatedAt(), "Should have creation timestamp");
        assertNotNull(entity.getUpdatedAt(), "Should have update timestamp");

        System.out.println("✅ Mock AI Analysis and Metadata Test Passed");
        System.out.println("   - Vector ID: " + entity.getVectorId().length());
        System.out.println("   - Searchable content length: " + entity.getSearchableContent().length());
        System.out.println("   - Metadata length: " + entity.getMetadata().length());
        System.out.println("   - Created at: " + entity.getCreatedAt());
    }

    @Test
    public void testMockBatchProcessing() {
        System.out.println("🧪 Testing Mock Batch Processing...");
        
        // Given - Create multiple products for batch processing
        List<TestProduct> products = List.of(
            TestProduct.builder()
                .name("AI Product 1")
                .description("First AI-powered product")
                .category("AI")
                .price(new BigDecimal("100.00"))
                .active(true)
                .build(),
            TestProduct.builder()
                .name("AI Product 2")
                .description("Second AI-powered product")
                .category("AI")
                .price(new BigDecimal("200.00"))
                .active(true)
                .build(),
            TestProduct.builder()
                .name("AI Product 3")
                .description("Third AI-powered product")
                .category("AI")
                .price(new BigDecimal("300.00"))
                .active(true)
                .build()
        );

        // When - Process all products in batch
        products = productRepository.saveAll(products);
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Then - Verify all products were processed
        List<AISearchableEntity> allEntities = searchRepository.findByEntityType("test-product");
        assertEquals(3, allEntities.size(), "Should process all three products");

        // Verify each entity has proper processing
        for (AISearchableEntity entity : allEntities) {
            assertNotNull(entity.getVectorId(), "Each entity should have embeddings");
            assertTrue(entity.getVectorId().length() >= 30, "Each entity should have substantial embeddings");
            assertNotNull(entity.getSearchableContent(), "Each entity should have searchable content");
            assertTrue(entity.getSearchableContent().contains("AI Product"), "Each entity should contain product name");
        }

        System.out.println("✅ Mock Batch Processing Test Passed");
        System.out.println("   - Products processed: " + allEntities.size());
        System.out.println("   - All products have vector IDs: " + 
            allEntities.stream().allMatch(e -> e.getVectorId() != null && !e.getVectorId().isEmpty()));
    }
}
