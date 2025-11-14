package com.ai.infrastructure.it;

import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.repository.IntentHistoryRepository;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Discovers available LLM and embedding providers at runtime and executes a smoke
 * Real API scenario for every combination.
 *
 * <p>The goal is to remove the need for hard-coding provider selections in the
 * integration suite. Whenever a new provider module is added (and reports as
 * available), it automatically becomes part of the matrix.</p>
 */
class RealAPIProviderMatrixIntegrationTest {

    static {
        RealAPITestSupport.ensureOpenAIConfigured();
    }

    @TestFactory
    Stream<DynamicTest> providerMatrix() {
        List<ProviderCombination> combinations = discoverProviderMatrix();

        Assertions.assertThat(combinations)
            .as("At least one provider combination should be available for dynamic Real API tests")
            .isNotEmpty();

        return combinations.stream()
            .map(combo -> DynamicTest.dynamicTest(
                String.format("LLM=%s | Embedding=%s", combo.llmProvider(), combo.embeddingProvider()),
                () -> runScenario(combo)
            ));
    }

    private List<ProviderCombination> discoverProviderMatrix() {
        configureProviderProperties("openai", "onnx");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
            .profiles("real-api-test")
            .properties(Map.of(
                "ai.providers.llm-provider", "openai",
                "ai.providers.embedding-provider", "onnx"
            ))
            .run()) {

            AIProviderManager providerManager = context.getBean(AIProviderManager.class);
            List<String> llmProviders = providerManager.getAvailableProviders().stream()
                .map(AIProvider::getProviderName)
                .sorted()
                .collect(Collectors.toList());

            List<String> embeddingProviders = context.getBeansOfType(EmbeddingProvider.class).values().stream()
                .filter(EmbeddingProvider::isAvailable)
                .map(EmbeddingProvider::getProviderName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

            List<ProviderCombination> combinations = new ArrayList<>();
            for (String llm : llmProviders) {
                for (String embedding : embeddingProviders) {
                    combinations.add(new ProviderCombination(llm, embedding));
                }
            }
            return combinations;
        } finally {
            clearProviderProperties();
        }
    }

    private void runScenario(ProviderCombination combination) {
        configureProviderProperties(combination.llmProvider(), combination.embeddingProvider());

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
            .profiles("real-api-test")
            .properties(Map.of(
                "ai.providers.llm-provider", combination.llmProvider(),
                "ai.providers.embedding-provider", combination.embeddingProvider()
            ))
            .run()) {

            // Obtain required beans
            AICapabilityService capabilityService = context.getBean(AICapabilityService.class);
            VectorManagementService vectorManagementService = context.getBean(VectorManagementService.class);
            TestProductRepository productRepository = context.getBean(TestProductRepository.class);
            AISearchableEntityRepository searchableEntityRepository = context.getBean(AISearchableEntityRepository.class);
            IntentHistoryRepository intentHistoryRepository = context.getBean(IntentHistoryRepository.class);
            RAGOrchestrator orchestrator = context.getBean(RAGOrchestrator.class);

            // Reset state
            vectorManagementService.clearAllVectors();
            searchableEntityRepository.deleteAll();
            productRepository.deleteAll();
            intentHistoryRepository.deleteAll();

            TestProduct product = TestProduct.builder()
                .name("Dynamic Provider Matrix Product")
                .description("""
                    This product validates dynamic provider selection.
                    It exercises both LLM and embedding providers within the Real API test run.
                    """)
                .category("Testing")
                .brand("MatrixBrand")
                .price(new BigDecimal("123.45"))
                .sku("MATRIX-001")
                .stockQuantity(50)
                .active(true)
                .build();

            product = productRepository.save(product);
            capabilityService.processEntityForAI(product, "test-product");

            Assertions.assertThat(searchableEntityRepository.count())
                .as("Searchable entities should be created for provider combo %s", combination)
                .isGreaterThanOrEqualTo(1L);

            Assertions.assertThat(
                vectorManagementService.vectorExists("test-product", String.valueOf(product.getId()))
            ).as("Vector should exist for provider combo %s", combination)
             .isTrue();

            OrchestrationResult orchestrationResult =
                orchestrator.orchestrate("Tell me about the Dynamic Provider Matrix Product", "provider-matrix-user");

            Assertions.assertThat(orchestrationResult)
                .as("Orchestration result should be present for %s", combination)
                .isNotNull();
            Assertions.assertThat(orchestrationResult.isSuccess())
                .as("Orchestration should succeed for %s", combination)
                .isTrue();
        } finally {
            clearProviderProperties();
        }
    }

    private void configureProviderProperties(String llmProvider, String embeddingProvider) {
        System.setProperty("LLM_PROVIDER", llmProvider);
        System.setProperty("ai.providers.llm-provider", llmProvider);
        System.setProperty("EMBEDDING_PROVIDER", embeddingProvider);
        System.setProperty("ai.providers.embedding-provider", embeddingProvider);
    }

    private void clearProviderProperties() {
        System.clearProperty("LLM_PROVIDER");
        System.clearProperty("ai.providers.llm-provider");
        System.clearProperty("EMBEDDING_PROVIDER");
        System.clearProperty("ai.providers.embedding-provider");
    }

    private record ProviderCombination(String llmProvider, String embeddingProvider) {
        @Override
        public String toString() {
            return llmProvider + "/" + embeddingProvider;
        }
    }
}
