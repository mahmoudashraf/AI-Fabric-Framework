package com.ai.infrastructure.it;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * RealAPI coverage that purpose-based routing is exercised end-to-end.
 *
 * <p>We assert purposes are propagated to {@link AICoreService}:
 * - intent extraction uses {@link LlmPurpose#ORCHESTRATION}
 * - RAG answer generation uses {@link LlmPurpose#GENERATION}</p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPILlmPurposePropagationIntegrationTest {

    static {
        RealAPITestSupport.ensureProviderConfigured();
        RealAPITestSupport.ensureLLMProviderSet();
        System.setProperty("EMBEDDING_PROVIDER",
            System.getProperty("EMBEDDING_PROVIDER", System.getenv("EMBEDDING_PROVIDER") != null ? System.getenv("EMBEDDING_PROVIDER") : "onnx"));
        System.setProperty("ai.providers.embedding-provider",
            System.getProperty("ai.providers.embedding-provider", System.getenv("EMBEDDING_PROVIDER") != null ? System.getenv("EMBEDDING_PROVIDER") : "onnx"));
    }

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private AISearchableEntityStorageStrategy storageStrategy;

    @Autowired
    private TestProductRepository productRepository;

    @SpyBean
    private AICoreService aiCoreService;

    @SpyBean
    private IntentQueryExtractor intentQueryExtractor;

    @BeforeEach
    void setUp() {
        assumeRealApiConfigured();
        Mockito.reset(aiCoreService, intentQueryExtractor);
        vectorManagementService.clearAllVectors();
        storageStrategy.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void intentExtractionShouldUseOrchestrationPurpose() {
        OrchestrationContext context = OrchestrationContext.forUser("purpose-orchestration-user");
        MultiIntentResponse response = intentQueryExtractor.extract(
            "Search the knowledge base for CyberShield incident response capabilities.",
            context
        );

        assertThat(response).isNotNull();
        verify(aiCoreService, atLeastOnce()).generateContent(any(), eq(LlmPurpose.ORCHESTRATION));
    }

    @Test
    void ragAnswerGenerationShouldUseGenerationPurpose() {
        seedProduct();

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("kb_search")
            .vectorSpace("test-product")
            .optimizedQuery("CyberShield incident response playbooks")
            .requiresRetrieval(true)
            .requiresGeneration(true)
            .build();

        doReturn(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .when(intentQueryExtractor)
            .extract(eq("force-generation-purpose"), any(OrchestrationContext.class));

        OrchestrationResult result = orchestrator.orchestrate("force-generation-purpose", "purpose-generation-user");
        assertThat(result).isNotNull();
        assertThat(result.getType()).isNotNull();

        verify(aiCoreService, atLeastOnce()).generateText(anyString(), eq(LlmPurpose.GENERATION));
    }

    private void seedProduct() {
        TestProduct product = productRepository.save(TestProduct.builder()
            .name("Enterprise Security Suite")
            .description("CyberShield provides incident response playbooks and automated containment workflows.")
            .category("Security")
            .brand("CyberShield")
            .price(new BigDecimal("2999.99"))
            .sku("CS-ENT-001")
            .stockQuantity(100)
            .active(true)
            .build());
        capabilityService.processEntityForAI(product, "test-product");
    }

    private void assumeRealApiConfigured() {
        boolean hasOpenAI = StringUtils.hasText(System.getProperty("OPENAI_API_KEY")) ||
                            StringUtils.hasText(System.getenv("OPENAI_API_KEY"));
        boolean hasAnthropic = StringUtils.hasText(System.getProperty("ANTHROPIC_API_KEY")) ||
                               StringUtils.hasText(System.getenv("ANTHROPIC_API_KEY"));
        boolean hasGemini = StringUtils.hasText(System.getProperty("GEMINI_API_KEY")) ||
                            StringUtils.hasText(System.getenv("GEMINI_API_KEY"));
        boolean hasCohere = StringUtils.hasText(System.getProperty("COHERE_API_KEY")) ||
                            StringUtils.hasText(System.getenv("COHERE_API_KEY"));
        boolean hasAzure = StringUtils.hasText(System.getProperty("AZURE_API_KEY")) ||
                           StringUtils.hasText(System.getenv("AZURE_API_KEY"));

        Assumptions.assumeTrue(
            hasOpenAI || hasAnthropic || hasGemini || hasCohere || hasAzure,
            "No real LLM provider API key configured; skipping LlmPurpose propagation RealAPI test."
        );
    }
}
