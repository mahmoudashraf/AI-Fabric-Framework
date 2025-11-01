package com.ai.infrastructure.it;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.support.OnnxBackedEmbeddingService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that validates hybrid AI processing where embeddings are generated locally
 * via ONNX while higher-level AI analysis still routes through OpenAI.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@Import(OnnxHybridIntegrationTest.OnnxEmbeddingTestConfig.class)
@Transactional
public class OnnxHybridIntegrationTest {

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private TestProductRepository productRepository;

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
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            vectorManagementService.clearAllVectors();
        } catch (Exception ignored) {
            // Ignore cleanup issues; Lucene directory cleanup below handles residual state.
        }

        Path indexPath = Paths.get("data", "test-lucene-index");
        if (Files.exists(indexPath)) {
            Files.walk(indexPath)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void testOnnxEmbeddingsAreStoredAndSearchable() {
        assertTrue(embeddingService instanceof OnnxBackedEmbeddingService, "ONNX embedding service should be active");

        TestProduct product = TestProduct.builder()
            .name("AI Smart Hub")
            .description("An AI-powered smart home hub that automates routines, optimizes energy usage, and provides real-time security insights.")
            .category("Smart Home")
            .brand("FutureTech")
            .price(new java.math.BigDecimal("449.99"))
            .sku("HUB-AI-001")
            .stockQuantity(25)
            .active(true)
            .build();

        product = productRepository.save(product);
        capabilityService.processEntityForAI(product, "test-product");

        List<AISearchableEntity> entities = searchableEntityRepository.findByEntityType("test-product");
        assertEquals(1, entities.size(), "Exactly one searchable entity should be indexed");

        AISearchableEntity entity = entities.get(0);
        assertNotNull(entity.getVectorId(), "Stored entity should contain a vector reference");
        assertFalse(entity.getVectorId().isEmpty(), "Vector reference must not be empty");
        assertNotNull(entity.getAiAnalysis(), "AI analysis should be generated via OpenAI");
        assertTrue(entity.getAiAnalysis().length() > 50, "AI analysis should contain meaningful content");

        var queryEmbedding = embeddingService.generateEmbedding(AIEmbeddingRequest.builder()
            .text("smart home automation with AI insights")
            .build());
        assertEquals(32, queryEmbedding.getDimensions(), "ONNX embeddings should have deterministic dimensions");

    }

    @TestConfiguration
    static class OnnxEmbeddingTestConfig {

        @Bean
        @Primary
        AIEmbeddingService onnxEmbeddingService(AIProviderConfig config, ResourceLoader resourceLoader) throws IOException {
            Resource resource = resourceLoader.getResource("classpath:models/text-identity-encoder.onnx");
            byte[] modelBytes;
            try (var inputStream = resource.getInputStream()) {
                modelBytes = StreamUtils.copyToByteArray(inputStream);
            }
            return new OnnxBackedEmbeddingService(config, modelBytes);
        }
    }
}
