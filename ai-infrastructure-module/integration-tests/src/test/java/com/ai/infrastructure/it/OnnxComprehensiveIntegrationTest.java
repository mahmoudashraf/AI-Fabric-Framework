package com.ai.infrastructure.it;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.TestArticle;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.entity.TestUser;
import com.ai.infrastructure.it.repository.TestArticleRepository;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.repository.TestUserRepository;
import com.ai.infrastructure.it.support.OnnxBackedEmbeddingService;
import com.ai.infrastructure.it.support.OnnxTestConfiguration;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@Import(OnnxTestConfiguration.class)
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OnnxComprehensiveIntegrationTest {

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestArticleRepository articleRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AIEmbeddingService embeddingService;

    @BeforeEach
    void cleanDatabase() {
        searchableEntityRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        articleRepository.deleteAll();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            vectorManagementService.clearAllVectors();
        } catch (Exception ignored) {
            // reset handled below if Lucene created any state
        }

        Path indexPath = Paths.get("data", "test-lucene-index");
        if (Files.exists(indexPath)) {
            Files.walk(indexPath)
                .sorted(Comparator.reverseOrder())
                .filter(path -> !path.equals(indexPath))
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void testComprehensiveWorkflowWithOnnxEmbeddings() {
        assertTrue(embeddingService instanceof OnnxBackedEmbeddingService, "ONNX embedding service should be active");

        TestUser author = userRepository.save(TestUser.builder()
            .firstName("Alicia")
            .lastName("Nguyen")
            .email("alicia.nguyen@example.com")
            .bio("AI researcher focused on applied machine learning for retail optimization")
            .active(true)
            .build());

        TestArticle article = articleRepository.save(TestArticle.builder()
            .title("AI-Driven Personalization for Online Retail")
            .content("This article explores how AI personalization pipelines combining embeddings and generative agents improve conversion rates across digital storefronts.")
            .summary("AI personalization boosts online retail conversion")
            .author("Alicia Nguyen")
            .tags("AI,Personalization,Retail")
            .published(true)
            .publishDate(LocalDateTime.now())
            .readTime(9)
            .viewCount(1200)
            .build());

        TestProduct product = productRepository.save(TestProduct.builder()
            .name("AI Personalization Suite")
            .description("Comprehensive AI suite delivering personalization, smart recommendations, and customer journey analytics")
            .category("Software")
            .brand("InsightAI")
            .price(new BigDecimal("7499.00"))
            .sku("AI-SUITE-2025")
            .stockQuantity(15)
            .active(true)
            .build());

        capabilityService.processEntityForAI(author, "test-user");
        capabilityService.processEntityForAI(article, "test-article");
        capabilityService.processEntityForAI(product, "test-product");

        List<AISearchableEntity> users = searchableEntityRepository.findByEntityType("test-user");
        List<AISearchableEntity> articles = searchableEntityRepository.findByEntityType("test-article");
        List<AISearchableEntity> products = searchableEntityRepository.findByEntityType("test-product");

        assertEquals(1, users.size(), "Should index the author");
        assertEquals(1, articles.size(), "Should index the article");
        assertEquals(1, products.size(), "Should index the product");

        AISearchableEntity productEntity = products.get(0);
        assertNotNull(productEntity.getVectorId());
        assertTrue(productEntity.getVectorId().length() >= 10, "Vector reference should be present");

        var queryEmbedding = embeddingService.generateEmbedding(AIEmbeddingRequest.builder()
            .text("AI personalization platform for retail analytics")
            .build());

        AISearchRequest searchRequest = AISearchRequest.builder()
            .query("AI personalization platform for retail analytics")
            .entityType("test-product")
            .limit(5)
            .threshold(0.0)
            .build();

        AISearchResponse searchResponse = vectorManagementService.search(queryEmbedding.getEmbedding(), searchRequest);

        assertNotNull(searchResponse);
        assertNotNull(searchResponse.getResults());

        assertNotNull(users.get(0).getAiAnalysis(), "Author should have mock analysis generated via OpenAI");
        assertNotNull(articles.get(0).getAiAnalysis(), "Article should have mock analysis generated via OpenAI");
    }
}
