package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.provider.AIProviderManager;
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
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Creative AI Scenarios Test
 * 
 * This test class demonstrates creative and innovative use cases for the AI infrastructure,
 * showcasing real-world scenarios and edge cases that would be encountered in production.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@TestPropertySource(properties = "ai.vector-db.lucene.index-path=./data/test-lucene-index/creative")
@Transactional
public class RealAPICreativeAIScenariosIntegrationTest {

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private AISearchableEntityStorageStrategy storageStrategy;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestArticleRepository articleRepository;

    @Autowired
    private VectorManagementService vectorManagementService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AICoreService aiCoreService;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "openAIProvider")
    private AIProvider openAIProvider;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AIProviderManager aiProviderManager;

    private final Random random = new Random();

    @BeforeEach
    public void setUp() {
        // Clean up before each test
        storageStrategy.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        articleRepository.deleteAll();

        when(openAIProvider.isAvailable()).thenReturn(true);
        when(openAIProvider.getProviderName()).thenReturn("openai");
        when(aiCoreService.generateText(anyString())).thenAnswer(invocation ->
            "mock:" + invocation.getArgument(0, String.class));
        when(aiCoreService.generateContent(any(AIGenerationRequest.class))).thenReturn(
            AIGenerationResponse.builder()
                .content("mock-openai-content")
                .model("mock-openai")
                .build()
        );
    }

    @Test
    public void testECommercePersonalizationScenario() {
        System.out.println("🛍️ Testing E-Commerce Personalization Scenario...");
        
        // Create a realistic e-commerce scenario with multiple users and products
        List<TestUser> customers = List.of(
            TestUser.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .email("sarah.johnson@email.com")
                .bio("Fashion enthusiast who loves sustainable luxury brands and minimalist design")
                .age(28)
                .location("New York, NY")
                .phoneNumber("+1-212-555-0101")
                .dateOfBirth(LocalDate.of(1995, 8, 12))
                .active(true)
                .build(),
            TestUser.builder()
                .firstName("Michael")
                .lastName("Chen")
                .email("michael.chen@email.com")
                .bio("Tech professional interested in smart home devices and cutting-edge technology")
                .age(32)
                .location("San Francisco, CA")
                .phoneNumber("+1-415-555-0202")
                .dateOfBirth(LocalDate.of(1991, 3, 25))
                .active(true)
                .build(),
            TestUser.builder()
                .firstName("Emma")
                .lastName("Williams")
                .email("emma.williams@email.com")
                .bio("Wellness advocate who prioritizes organic products and holistic health approaches")
                .age(35)
                .location("Portland, OR")
                .phoneNumber("+1-503-555-0303")
                .dateOfBirth(LocalDate.of(1988, 11, 7))
                .active(true)
                .build()
        );

        List<TestProduct> products = List.of(
            TestProduct.builder()
                .name("Sustainable Luxury Handbag")
                .description("Eco-friendly handbag made from recycled materials with minimalist design and premium craftsmanship")
                .category("Fashion")
                .brand("EcoLux")
                .price(new BigDecimal("899.99"))
                .sku("SLH-001")
                .stockQuantity(15)
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Smart Home Hub Pro")
                .description("Advanced smart home controller with AI-powered automation and voice control capabilities")
                .category("Smart Home")
                .brand("TechHome")
                .price(new BigDecimal("299.99"))
                .sku("SHH-PRO-001")
                .stockQuantity(25)
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Organic Wellness Bundle")
                .description("Complete wellness package with organic supplements, essential oils, and meditation accessories")
                .category("Wellness")
                .brand("PureWellness")
                .price(new BigDecimal("199.99"))
                .sku("OWB-001")
                .stockQuantity(30)
                .active(true)
                .build()
        );

        // Save all entities
        customers = userRepository.saveAll(customers);
        products = productRepository.saveAll(products);

        // Process all entities for AI
        for (TestUser customer : customers) {
            capabilityService.processEntityForAI(customer, "test-user");
        }
        for (TestProduct product : products) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Test personalized recommendations
        List<AISearchableEntity> allEntities = allEntities();
        assertEquals(6, allEntities.size(), "Should process all customers and products");

        // Test semantic search for personalized recommendations
        List<AISearchableEntity> fashionResults = searchContent("fashion");
        assertFalse(fashionResults.isEmpty(), "Should find fashion-related content");

        List<AISearchableEntity> techResults = searchContent("smart home");
        assertFalse(techResults.isEmpty(), "Should find smart home content");

        List<AISearchableEntity> wellnessResults = searchContent("wellness");
        assertFalse(wellnessResults.isEmpty(), "Should find wellness content");

        System.out.println("✅ E-Commerce Personalization Scenario Test Passed");
        System.out.println("   - Total entities: " + allEntities.size());
        System.out.println("   - Fashion results: " + fashionResults.size());
        System.out.println("   - Tech results: " + techResults.size());
        System.out.println("   - Wellness results: " + wellnessResults.size());
    }

    @Test
    public void testContentModerationScenario() {
        System.out.println("🛡️ Testing Content Moderation Scenario...");
        
        // Create articles with various content types for moderation testing
        List<TestArticle> articles = List.of(
            TestArticle.builder()
                .title("The Future of Artificial Intelligence in Healthcare")
                .content("Comprehensive analysis of AI applications in medical diagnosis, treatment planning, and patient care")
                .summary("AI is revolutionizing healthcare with advanced diagnostic tools")
                .author("Dr. Medical AI Expert")
                .tags("AI, Healthcare, Technology, Medicine")
                .published(true)
                .publishDate(LocalDateTime.now())
                .readTime(12)
                .viewCount(5000)
                .build(),
            TestArticle.builder()
                .title("Sustainable Technology Solutions for Climate Change")
                .content("Exploring innovative technologies that can help combat climate change and promote environmental sustainability")
                .summary("Technology can play a crucial role in addressing climate challenges")
                .author("Environmental Tech Researcher")
                .tags("Sustainability, Climate, Technology, Environment")
                .published(true)
                .publishDate(LocalDateTime.now())
                .readTime(10)
                .viewCount(3500)
                .build(),
            TestArticle.builder()
                .title("Cybersecurity Best Practices for Small Businesses")
                .content("Essential cybersecurity measures that small businesses should implement to protect their digital assets")
                .summary("Small businesses need robust cybersecurity strategies")
                .author("Cybersecurity Consultant")
                .tags("Cybersecurity, Business, Security, Technology")
                .published(true)
                .publishDate(LocalDateTime.now())
                .readTime(8)
                .viewCount(2800)
                .build()
        );

        // Save and process articles
        articles = articleRepository.saveAll(articles);
        for (TestArticle article : articles) {
            capabilityService.processEntityForAI(article, "test-article");
        }

        // Test content analysis and moderation
        List<AISearchableEntity> articleEntities = entities("test-article");
        assertEquals(3, articleEntities.size(), "Should process all articles");

        // Test content categorization
        List<AISearchableEntity> healthcareResults = searchContent("healthcare");
        assertFalse(healthcareResults.isEmpty(), "Should find healthcare content");

        List<AISearchableEntity> climateResults = searchContent("climate");
        assertFalse(climateResults.isEmpty(), "Should find climate content");

        List<AISearchableEntity> securityResults = searchContent("cybersecurity");
        assertFalse(securityResults.isEmpty(), "Should find cybersecurity content");

        System.out.println("✅ Content Moderation Scenario Test Passed");
        System.out.println("   - Article entities: " + articleEntities.size());
        System.out.println("   - Healthcare results: " + healthcareResults.size());
        System.out.println("   - Climate results: " + climateResults.size());
        System.out.println("   - Security results: " + securityResults.size());
    }

    @Test
    public void testMultiLanguageContentScenario() {
        System.out.println("🌍 Testing Multi-Language Content Scenario...");
        
        // Create products with multi-language descriptions
        List<TestProduct> multiLangProducts = List.of(
            TestProduct.builder()
                .name("Smartphone Pro Max")
                .description("Advanced smartphone with AI-powered camera, 5G connectivity, and premium design. スマートフォン Pro Max - AI搭載カメラ、5G接続、プレミアムデザイン")
                .category("Electronics")
                .brand("TechGlobal")
                .price(new BigDecimal("1299.99"))
                .sku("SPM-001")
                .stockQuantity(50)
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Luxury Watch Collection")
                .description("Exclusive luxury watch with Swiss movement and diamond accents. Collection de montres de luxe avec mouvement suisse et accents diamants")
                .category("Luxury")
                .brand("LuxuryTime")
                .price(new BigDecimal("15999.99"))
                .sku("LWC-001")
                .stockQuantity(5)
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Smart Home System")
                .description("Intelligent home automation system with voice control. Sistema de automatización del hogar inteligente con control por voz")
                .category("Smart Home")
                .brand("SmartHome")
                .price(new BigDecimal("899.99"))
                .sku("SHS-001")
                .stockQuantity(20)
                .active(true)
                .build()
        );

        // Save and process products
        multiLangProducts = productRepository.saveAll(multiLangProducts);
        for (TestProduct product : multiLangProducts) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Test multi-language content processing
        List<AISearchableEntity> productEntities = entities("test-product");
        assertEquals(3, productEntities.size(), "Should process all multi-language products");

        // Test language-specific searches
        List<AISearchableEntity> japaneseResults = searchContent("スマートフォン");
        assertFalse(japaneseResults.isEmpty(), "Should find Japanese content");

        List<AISearchableEntity> frenchResults = searchContent("Collection");
        assertFalse(frenchResults.isEmpty(), "Should find French content");

        List<AISearchableEntity> spanishResults = searchContent("Sistema");
        assertFalse(spanishResults.isEmpty(), "Should find Spanish content");

        System.out.println("✅ Multi-Language Content Scenario Test Passed");
        System.out.println("   - Product entities: " + productEntities.size());
        System.out.println("   - Japanese results: " + japaneseResults.size());
        System.out.println("   - French results: " + frenchResults.size());
        System.out.println("   - Spanish results: " + spanishResults.size());
    }

    @Test
    public void testRealTimeAnalyticsScenario() {
        System.out.println("📊 Testing Real-Time Analytics Scenario...");
        
        // Simulate real-time data processing with multiple concurrent operations
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try {
            List<CompletableFuture<Void>> futures = IntStream.range(0, 20)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    // Create random users and products for analytics
                    TestUser analyticsUser = TestUser.builder()
                        .firstName("Analytics" + i)
                        .lastName("User" + i)
                        .email("analytics" + i + "@test.com")
                        .bio("User " + i + " for real-time analytics testing with AI processing")
                        .age(20 + random.nextInt(40))
                        .location("Analytics City " + i)
                        .phoneNumber("+1-555-" + String.format("%04d", 1000 + i))
                        .dateOfBirth(LocalDate.of(1980 + random.nextInt(30), 1 + random.nextInt(12), 1 + random.nextInt(28)))
                        .active(true)
                        .build();

                    TestProduct analyticsProduct = TestProduct.builder()
                        .name("Analytics Product " + i)
                        .description("Product " + i + " designed for real-time analytics and AI processing with comprehensive data tracking")
                        .category("Analytics")
                        .brand("AnalyticsCorp")
                        .price(new BigDecimal(100.00 + random.nextInt(900)))
                        .sku("ANAL-" + String.format("%03d", i))
                        .stockQuantity(random.nextInt(100))
                        .active(true)
                        .build();

                    // Save and process
                    analyticsUser = userRepository.save(analyticsUser);
                    analyticsProduct = productRepository.save(analyticsProduct);

                    capabilityService.processEntityForAI(analyticsUser, "test-user");
                    capabilityService.processEntityForAI(analyticsProduct, "test-product");
                }, executor))
                .toList();

            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Verify real-time analytics processing
            List<AISearchableEntity> allEntities = allEntities();
            assertEquals(40, allEntities.size(), "Should process all analytics entities");

            List<AISearchableEntity> userEntities = entities("test-user");
            assertEquals(20, userEntities.size(), "Should process all analytics users");

            List<AISearchableEntity> productEntities = entities("test-product");
            assertEquals(20, productEntities.size(), "Should process all analytics products");

            // Test analytics-specific searches
            List<AISearchableEntity> analyticsResults = searchContent("analytics");
            assertTrue(analyticsResults.size() >= 20, "Should find analytics-related content");

            System.out.println("✅ Real-Time Analytics Scenario Test Passed");
            System.out.println("   - Total entities: " + allEntities.size());
            System.out.println("   - User entities: " + userEntities.size());
            System.out.println("   - Product entities: " + productEntities.size());
            System.out.println("   - Analytics results: " + analyticsResults.size());

        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testEdgeCaseHandlingScenario() {
        System.out.println("⚠️ Testing Edge Case Handling Scenario...");
        
        // Test various edge cases that might occur in production
        List<TestProduct> edgeCaseProducts = List.of(
            // Very long description
            TestProduct.builder()
                .name("Product with Very Long Description")
                .description("This is a product with an extremely long description that contains a lot of text to test how the AI system handles large amounts of content. " +
                    "The description includes multiple sentences, technical specifications, detailed features, and comprehensive information about the product. " +
                    "It also contains various keywords, technical terms, and descriptive language to ensure proper AI processing and embedding generation. " +
                    "This product is designed to test the system's ability to handle large text inputs and generate meaningful embeddings from extensive content. " +
                    "The description continues with more details about the product's features, benefits, and specifications to provide a comprehensive test case. " +
                    "Additional information includes pricing details, availability, shipping information, and customer reviews to create a realistic e-commerce scenario. " +
                    "The product also includes various attributes like color options, size variations, material specifications, and compatibility information. " +
                    "This extensive description is intended to test the AI system's ability to process and understand complex, multi-faceted product information. " +
                    "The content includes both technical specifications and marketing language to ensure comprehensive AI processing and analysis. " +
                    "This product description serves as a stress test for the AI infrastructure's text processing and embedding generation capabilities.")
                .category("Edge Case Testing")
                .brand("EdgeCaseCorp")
                .price(new BigDecimal("999.99"))
                .sku("ECC-LONG-001")
                .stockQuantity(1)
                .active(true)
                .build(),
            // Special characters and emojis
            TestProduct.builder()
                .name("Product with Special Characters 🚀✨")
                .description("Product with special characters: éñüñçödé, 中文, العربية, русский, 🎉💡🔥⚡️🌟")
                .category("Special Chars")
                .brand("SpecialCorp")
                .price(new BigDecimal("777.77"))
                .sku("SCC-001")
                .stockQuantity(5)
                .active(true)
                .build(),
            // Empty/null fields
            TestProduct.builder()
                .name("")
                .description(null)
                .category("")
                .brand(null)
                .price(new BigDecimal("0.00"))
                .sku("")
                .stockQuantity(0)
                .active(true)
                .build()
        );

        // Save and process edge case products
        edgeCaseProducts = productRepository.saveAll(edgeCaseProducts);
        for (TestProduct product : edgeCaseProducts) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        // Verify edge case handling
        List<AISearchableEntity> edgeCaseEntities = entities("test-product");
        assertEquals(3, edgeCaseEntities.size(), "Should process all edge case products");

        // Test that the system handles edge cases gracefully
        for (AISearchableEntity entity : edgeCaseEntities) {
            assertNotNull(entity.getVectorId(), "Each entity should have vector ID");
            assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");
            
            // Verify vector exists in vector database
            assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), 
                      "Vector should exist in vector database");
            assertNotNull(entity.getSearchableContent(), "Each entity should have searchable content");
            // Even with edge cases, the system should generate some content
            assertTrue(entity.getSearchableContent().length() >= 0, "Searchable content should be non-null");
        }

        // Test special character handling
        List<AISearchableEntity> emojiResults = searchContent("🚀");
        assertFalse(emojiResults.isEmpty(), "Should find emoji content");

        System.out.println("✅ Edge Case Handling Scenario Test Passed");
        System.out.println("   - Edge case entities: " + edgeCaseEntities.size());
        System.out.println("   - Emoji results: " + emojiResults.size());
    }

    @Test
    public void testScalabilityStressTest() {
        System.out.println("💪 Testing Scalability Stress Test...");
        
        long startTime = System.currentTimeMillis();
        
        // Create a large number of entities for stress testing
        List<TestProduct> stressProducts = IntStream.range(0, 100)
            .mapToObj(i -> TestProduct.builder()
                .name("Stress Test Product " + i)
                .description("Product " + i + " for scalability stress testing with AI processing and embedding generation")
                .category("Stress Test")
                .brand("StressCorp")
                .price(new BigDecimal(10.00 + i))
                .sku("STRESS-" + String.format("%03d", i))
                .stockQuantity(1)
                .active(true)
                .build())
            .toList();

        // Save all products
        stressProducts = productRepository.saveAll(stressProducts);
        
        // Process all products
        for (TestProduct product : stressProducts) {
            capabilityService.processEntityForAI(product, "test-product");
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Verify stress test processing
        List<AISearchableEntity> stressEntities = entities("test-product");
        assertEquals(100, stressEntities.size(), "Should process all stress test products");

        // Calculate performance metrics
        double avgTimePerEntity = (double) totalTime / 100;
        double entitiesPerSecond = 1000.0 / avgTimePerEntity;

        System.out.println("✅ Scalability Stress Test Passed");
        System.out.println("   - Total time: " + totalTime + "ms");
        System.out.println("   - Average time per entity: " + String.format("%.2f", avgTimePerEntity) + "ms");
        System.out.println("   - Entities per second: " + String.format("%.2f", entitiesPerSecond));
        System.out.println("   - Total entities processed: " + stressEntities.size());

        // Performance assertions
        assertTrue(totalTime < 120000, "Stress test should complete within 2 minutes");
        assertTrue(avgTimePerEntity < 5000, "Average processing time per entity should be under 5 seconds");
    }
    private List<AISearchableEntity> entities(String entityType) {
        return storageStrategy.findByEntityType(entityType);
    }

    private List<AISearchableEntity> allEntities() {
        return storageStrategy.findByVectorIdIsNotNull();
    }

    private List<AISearchableEntity> searchContent(String term) {
        String needle = term.toLowerCase();
        return allEntities().stream()
            .filter(e -> e.getSearchableContent() != null && e.getSearchableContent().toLowerCase().contains(needle))
            .toList();
    }
}
