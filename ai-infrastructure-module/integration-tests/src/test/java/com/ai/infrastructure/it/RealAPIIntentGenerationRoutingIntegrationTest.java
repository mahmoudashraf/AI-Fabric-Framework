package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPIIntentGenerationRoutingIntegrationTest {

    static {
        // Ensure provider configuration is present for real API calls
        RealAPITestSupport.ensureOpenAIConfigured();
        System.setProperty("LLM_PROVIDER", System.getProperty("LLM_PROVIDER", "openai"));
        System.setProperty("ai.providers.llm-provider", System.getProperty("ai.providers.llm-provider", "openai"));
        System.setProperty("EMBEDDING_PROVIDER", System.getProperty("EMBEDDING_PROVIDER", "onnx"));
        System.setProperty("ai.providers.embedding-provider", System.getProperty("ai.providers.embedding-provider", "onnx"));
    }

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private RAGOrchestrator orchestrator;

    @SpyBean
    private RAGService ragService;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @Autowired
    private TestProductRepository productRepository;

    @Autowired
    private AISearchableEntityRepository searchRepository;

    @BeforeEach
    void setUp() {
        vectorManagementService.clearAllVectors();
        searchRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void shouldRouteGenerationAndHonorOptimizedQueryWithRealProvider() {
        assumeRealApiConfigured();

        // Seed a product so retrieval has signal
        TestProduct headphones = persistProduct(
            "SonicWave Pro Headphones",
            "Premium over-ear headphones with spatial audio and active noise cancellation tuned for studio monitoring.",
            "Audio",
            "SonicWave",
            new BigDecimal("149.99")
        );
        capabilityService.processEntityForAI(headphones, "test-product");

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("recommend_products")
            .vectorSpace("test-product")
            .optimizedQuery("test-product entities where category = 'Audio' AND price < 200")
            .requiresGeneration(true)
            .build();

        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder().intents(List.of(intent)).build());

        OrchestrationResult result = orchestrator.orchestrate("Suggest an affordable audio headset", "real-gen-routing-user");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsKeys("ragResponse", "requiresGeneration");
        assertThat(result.getData().get("requiresGeneration")).isEqualTo(true);

        Object ragResponseObj = result.getData().get("ragResponse");
        Assertions.assertThat(ragResponseObj).isInstanceOf(RAGResponse.class);
        RAGResponse ragResponse = (RAGResponse) ragResponseObj;
        Map<String, Object> metadata = ragResponse.getMetadata();
        assertThat(metadata)
            .isNotNull()
            .containsEntry("optimizedQueryProvided", true)
            .containsEntry("requiresGeneration", true);
        assertThat(String.valueOf(metadata.get("embeddingQuery")).toLowerCase())
            .contains("audio")
            .contains("price");

        // Ensure generation path was taken
        ArgumentCaptor<RAGRequest> requestCaptor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragService).performRAGQuery(requestCaptor.capture());
        verify(ragService, never()).performRag(any());
        assertThat(requestCaptor.getValue().getMetadata()).containsEntry("optimizedQuery", intent.getOptimizedQuery());
    }

    private void assumeRealApiConfigured() {
        boolean hasKey = StringUtils.hasText(System.getenv("OPENAI_API_KEY"))
            || StringUtils.hasText(System.getProperty("OPENAI_API_KEY"));
        Assumptions.assumeTrue(hasKey, "OPENAI_API_KEY must be configured for real API tests");
    }

    private TestProduct persistProduct(String name, String description, String category, String brand, BigDecimal price) {
        TestProduct product = TestProduct.builder()
            .name(name)
            .description(description)
            .category(category)
            .brand(brand)
            .price(price)
            .sku("SKU-" + name.replaceAll("\\s+", "-"))
            .stockQuantity(25)
            .active(true)
            .build();
        return productRepository.save(product);
    }
}
