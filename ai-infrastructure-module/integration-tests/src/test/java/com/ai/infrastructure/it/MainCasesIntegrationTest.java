package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.TestArticle;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.entity.TestUser;
import com.ai.infrastructure.it.repository.TestArticleRepository;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.repository.TestUserRepository;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Main Cases Integration Test
 *
 * Exercises the primary AI infrastructure flows that most EasyLuxury
 * services rely on:
 *  - Processing the canonical Product, User, and Article entity types
 *  - Searching across entity types with shared keywords
 *  - Refreshing vectors when content changes
 *  - Cleaning up vectors and index entries when data is removed
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Transactional
class MainCasesIntegrationTest {

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestArticleRepository articleRepository;

    @BeforeEach
    void cleanDatabase() {
        searchRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        articleRepository.deleteAll();
        vectorManagementService.clearAllVectors();
    }

    @Test
    void shouldProcessPrimaryEntitiesAndCreateVectors() {
        TestProduct product = productRepository.save(TestProduct.builder()
            .name("AI Concierge Suite")
            .description("Luxury concierge operating system with cross-channel context and proactive outreach")
            .category("Luxury Services")
            .brand("EasyLuxury Labs")
            .price(new BigDecimal("4999.99"))
            .sku("CONCIERGE-OS")
            .stockQuantity(10)
            .active(true)
            .build());

        TestUser user = userRepository.save(TestUser.builder()
            .firstName("Amelia")
            .lastName("Stone")
            .email("amelia.stone@luxury.example")
            .bio("Chief experience officer curating ultra-personalized journeys with AI insights")
            .age(34)
            .location("Monaco")
            .phoneNumber("+377-555-0199")
            .dateOfBirth(LocalDate.of(1991, 3, 9))
            .active(true)
            .build());

        TestArticle article = articleRepository.save(TestArticle.builder()
            .title("Designing AI-First Luxury Journeys")
            .content("The AI concierge suite coordinates travel, dining, wellness, and art access across providers.")
            .summary("Coordinated AI journeys keep guests delighted across every touchpoint.")
            .author("Sophia Laurent")
            .tags("AI,Luxury,Concierge,Experience")
            .publishDate(LocalDateTime.now())
            .readTime(6)
            .published(true)
            .viewCount(1200)
            .build());

        capabilityService.processEntityForAI(product, "test-product");
        capabilityService.processEntityForAI(user, "test-user");
        capabilityService.processEntityForAI(article, "test-article");

        assertEquals(3, searchRepository.count(), "All main entities should be indexed");

        AISearchableEntity productSearch = searchRepository.findByEntityType("test-product").get(0);
        AISearchableEntity userSearch = searchRepository.findByEntityType("test-user").get(0);
        AISearchableEntity articleSearch = searchRepository.findByEntityType("test-article").get(0);

        assertNotNull(productSearch.getVectorId(), "Product should have a vector");
        assertNotNull(userSearch.getVectorId(), "User should have a vector");
        assertNotNull(articleSearch.getVectorId(), "Article should have a vector");

        assertTrue(vectorManagementService.vectorExists("test-product", product.getId().toString()));
        assertTrue(vectorManagementService.vectorExists("test-user", user.getId().toString()));
        assertTrue(vectorManagementService.vectorExists("test-article", article.getId().toString()));

        assertTrue(productSearch.getSearchableContent().contains("concierge"), "Product content should capture concierge context");
        assertTrue(userSearch.getSearchableContent().contains("journeys"), "User content should capture strategy keywords");
        assertTrue(articleSearch.getSearchableContent().contains("luxury"), "Article content should capture luxury details");
    }

    @Test
    void shouldSupportKeywordSearchAcrossEntityTypes() {
        List<TestProduct> products = productRepository.saveAll(List.of(
            TestProduct.builder()
                .name("Quantum Audio Gallery")
                .description("Immersive, concierge-configured audio experiences tuned by AI acoustics")
                .category("Soundscapes")
                .brand("Aurora Atelier")
                .price(new BigDecimal("7999.00"))
                .active(true)
                .build(),
            TestProduct.builder()
                .name("Serenity Wellness Capsule")
                .description("Restorative capsule with adaptive AI breath coaching and chromatherapy")
                .category("Wellness")
                .brand("Serenity Labs")
                .price(new BigDecimal("12999.00"))
                .active(true)
                .build()
        ));

        TestUser curator = userRepository.save(TestUser.builder()
            .firstName("Julian")
            .lastName("Reyes")
            .email("julian.reyes@concierge.example")
            .bio("Concierge strategist designing quantum-grade welcome moments and private concerts")
            .age(39)
            .location("New York")
            .phoneNumber("+1-212-555-0199")
            .dateOfBirth(LocalDate.of(1986, 2, 14))
            .active(true)
            .build());

        TestArticle spotlight = articleRepository.save(TestArticle.builder()
            .title("Concierge Playbook for Quantum Sound Immersion")
            .content("Private sound baths, curated playlists, and responsive lighting deliver concierge-level serenity.")
            .summary("Concierge teams translate AI insights into effortless calm.")
            .author("Annette Moreau")
            .tags("Concierge,Sound,Wellness")
            .publishDate(LocalDateTime.now())
            .readTime(5)
            .published(true)
            .viewCount(980)
            .build());

        products.forEach(product -> capabilityService.processEntityForAI(product, "test-product"));
        capabilityService.processEntityForAI(curator, "test-user");
        capabilityService.processEntityForAI(spotlight, "test-article");

        List<AISearchableEntity> quantumResults = searchRepository.findBySearchableContentContainingIgnoreCase("quantum");
        assertTrue(quantumResults.size() >= 2, "Quantum keyword should surface multiple entities");

        List<AISearchableEntity> conciergeResults = searchRepository.findBySearchableContentContainingIgnoreCase("concierge");
        assertTrue(conciergeResults.size() >= 2, "Concierge keyword should hit several entity types");

        Set<String> conciergeTypes = conciergeResults.stream()
            .map(AISearchableEntity::getEntityType)
            .collect(Collectors.toSet());

        assertTrue(conciergeTypes.contains("test-user"), "User entities should be searchable via concierge keyword");
        assertTrue(conciergeTypes.contains("test-article"), "Articles should be searchable via concierge keyword");
        assertTrue(conciergeTypes.contains("test-product"), "Products should also appear for concierge keyword");
    }

    @Test
    void shouldRefreshVectorsWhenEntityContentChanges() {
        TestProduct limitedEdition = productRepository.save(TestProduct.builder()
            .name("Limited Edition Travel Set")
            .description("Collector-grade travel system with AI packing cues and curated itineraries")
            .category("Travel")
            .brand("Voyage Atelier")
            .price(new BigDecimal("15999.50"))
            .sku("VOYAGE-LTD")
            .stockQuantity(4)
            .active(true)
            .build());

        capabilityService.processEntityForAI(limitedEdition, "test-product");

        AISearchableEntity initialEntry = searchRepository
            .findByEntityTypeAndEntityId("test-product", limitedEdition.getId().toString())
            .orElseThrow();
        String initialContent = initialEntry.getSearchableContent();

        limitedEdition.setDescription("Collector-grade travel system with quantum-grade finish and alpine-ready concierge tools");
        productRepository.save(limitedEdition);
        capabilityService.processEntityForAI(limitedEdition, "test-product");

        AISearchableEntity refreshedEntry = searchRepository
            .findByEntityTypeAndEntityId("test-product", limitedEdition.getId().toString())
            .orElseThrow();

        assertNotEquals(initialContent, refreshedEntry.getSearchableContent(), "Searchable content should reflect updates");
        assertTrue(refreshedEntry.getSearchableContent().contains("quantum-grade finish"),
            "Updated content should include the new descriptive phrase");
        assertTrue(vectorManagementService.vectorExists("test-product", limitedEdition.getId().toString()),
            "Vector should still exist after refresh");
    }

    @Test
    void shouldRemoveEntitiesAndVectorsWhenCleanupRuns() {
        TestProduct removableProduct = productRepository.save(TestProduct.builder()
            .name("Sunset Yacht Experience")
            .description("Private yacht charter scheduled through AI guest signals")
            .category("Experiences")
            .brand("Azure Fleet")
            .price(new BigDecimal("25999.00"))
            .active(true)
            .build());

        TestUser removableUser = userRepository.save(TestUser.builder()
            .firstName("Lena")
            .lastName("Hart")
            .email("lena.hart@sunset.example")
            .bio("Guest experience director focused on sunset activations")
            .age(32)
            .location("Ibiza")
            .phoneNumber("+34-555-0147")
            .dateOfBirth(LocalDate.of(1993, 7, 2))
            .active(true)
            .build());

        capabilityService.processEntityForAI(removableProduct, "test-product");
        capabilityService.processEntityForAI(removableUser, "test-user");

        assertEquals(2, searchRepository.count(), "Both entities should be indexed before cleanup");

        String productId = removableProduct.getId().toString();
        String userId = removableUser.getId().toString();

        capabilityService.removeEntityFromIndex(productId, "test-product");
        capabilityService.removeEntityFromIndex(userId, "test-user");

        vectorManagementService.removeVector("test-product", productId);
        vectorManagementService.removeVector("test-user", userId);

        assertFalse(vectorManagementService.vectorExists("test-product", productId), "Product vector should be removed");
        assertFalse(vectorManagementService.vectorExists("test-user", userId), "User vector should be removed");
        assertTrue(searchRepository.findByEntityType("test-product").isEmpty(), "Product index entries should be gone");
        assertTrue(searchRepository.findByEntityType("test-user").isEmpty(), "User index entries should be gone");
    }
}
