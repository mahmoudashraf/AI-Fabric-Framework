package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.dto.*;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.rag.RAGService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Search vs LLM Generation feature.
 * 
 * Tests the differentiation between:
 * 1. Search-only queries (requiresGeneration=false) - return raw results
 * 2. LLM generation queries (requiresGeneration=true) - return filtered LLM response
 */
@SpringBootTest
@ActiveProfiles("realapi-test")
@DisplayName("Search vs LLM Generation Feature Tests")
class SearchVsLLMGenerationIntegrationTest {

    @Autowired
    private RAGOrchestrator ragOrchestrator;

    @MockBean
    private RAGService ragService;

    @MockBean
    private AIEntityConfigurationLoader configurationLoader;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String PRODUCT_ENTITY_TYPE = "product";

    @BeforeEach
    void setUp() {
        setupProductConfiguration();
        mockIntentExtraction(createSearchOnlyIntent());
    }

    /**
     * Setup product entity configuration with include-in-rag flags
     */
    private void setupProductConfiguration() {
        AIEntityConfig config = AIEntityConfig.builder()
            .entityType(PRODUCT_ENTITY_TYPE)
            .enableSearch(true)
            .indexable(true)
            .build();

        // Configure searchable fields
        Map<String, SearchableFieldConfig> searchableFields = new HashMap<>();
        
        // Include in RAG: true
        searchableFields.put("name", SearchableFieldConfig.builder()
            .name("name")
            .includeInRag(true)
            .enableSemanticSearch(true)
            .weight(2.0)
            .build());
        
        searchableFields.put("description", SearchableFieldConfig.builder()
            .name("description")
            .includeInRag(true)
            .enableSemanticSearch(true)
            .weight(1.5)
            .build());
        
        // Include in RAG: false (searchable but hidden from LLM)
        searchableFields.put("costPrice", SearchableFieldConfig.builder()
            .name("costPrice")
            .includeInRag(false)
            .enableSemanticSearch(true)
            .weight(1.0)
            .build());
        
        // Include in RAG: false (not searchable, not visible to LLM)
        searchableFields.put("internalMargin", SearchableFieldConfig.builder()
            .name("internalMargin")
            .includeInRag(false)
            .enableSemanticSearch(false)
            .weight(0.0)
            .build());

        config.setSearchableFields(searchableFields);
        
        when(configurationLoader.getEntityConfig(PRODUCT_ENTITY_TYPE))
            .thenReturn(config);
    }

    /**
     * Create mock RAG response with sample product data
     */
    private RAGResponse createMockRAGResponse() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", "Luxury Leather Wallet");
        metadata.put("description", "Premium Italian leather, handcrafted");
        metadata.put("costPrice", 50.00);
        metadata.put("retailPrice", 199.99);
        metadata.put("internalMargin", 0.75);

        RAGResponse.RAGDocument document = RAGResponse.RAGDocument.builder()
            .id("prod-123")
            .metadata(metadata)
            .build();

        return RAGResponse.builder()
            .response("Found 1 matching product")
            .documents(List.of(document))
            .confidenceScore(0.92)
            .build();
    }

    /**
     * Create search-only intent (no LLM generation needed)
     */
    private Intent createSearchOnlyIntent() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("search_products_under_price")
            .requiresRetrieval(true)
            .requiresGeneration(false)  // ← Key flag
            .vectorSpace(PRODUCT_ENTITY_TYPE)
            .confidence(0.95)
            .build();
        intent.normalize();
        return intent;
    }

    /**
     * Create LLM generation intent (requires context filtering)
     */
    private Intent createLLMGenerationIntent() {
        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("should_buy_this_product")
            .requiresRetrieval(true)
            .requiresGeneration(true)  // ← Key flag
            .vectorSpace(PRODUCT_ENTITY_TYPE)
            .confidence(0.92)
            .build();
        intent.normalize();
        return intent;
    }

    /**
     * Stub intent extraction to return the provided intent.
     */
    private void mockIntentExtraction(Intent intent) {
        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(intent))
            .compound(false)
            .build();
        response.normalize();
        when(intentQueryExtractor.extract(anyString(), anyString()))
            .thenReturn(response);
    }

    @Nested
    @DisplayName("Search-Only Query Tests")
    class SearchOnlyQueryTests {

        @Test
        @DisplayName("Should return raw search results with all fields including costPrice")
        void testSearchOnlyReturnsAllFields() {
            // Arrange
            Intent searchIntent = createSearchOnlyIntent();
            mockIntentExtraction(searchIntent);
            RAGResponse mockResponse = createMockRAGResponse();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Show me wallets under $60",
                TEST_USER_ID
            );

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            @SuppressWarnings("unchecked")
            List<RAGResponse.RAGDocument> documents = (List<RAGResponse.RAGDocument>) data.get("documents");
            
            assertThat(documents).hasSize(1);
            RAGResponse.RAGDocument product = documents.get(0);
            Map<String, Object> metadata = product.getMetadata();
            
            // Verify all fields are present (no filtering for search-only)
            assertThat(metadata).containsKeys("name", "description", "costPrice", "internalMargin");
            assertThat(metadata.get("costPrice")).isEqualTo(50.00);
            assertThat(metadata.get("internalMargin")).isEqualTo(0.75);
        }

        @Test
        @DisplayName("Should not have contextFiltered flag in response")
        void testSearchOnlyNoContextFiltering() {
            // Arrange
            Intent searchIntent = createSearchOnlyIntent();
            mockIntentExtraction(searchIntent);
            RAGResponse mockResponse = createMockRAGResponse();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Show me wallets under $60",
                TEST_USER_ID
            );

            // Assert
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            
            assertThat(data).doesNotContainKey("contextFiltered");
            assertThat(data).doesNotContainKey("filteredContext");
        }

        @Test
        @DisplayName("Should maintain search performance (no LLM overhead)")
        void testSearchOnlyPerformance() {
            // Arrange
            Intent searchIntent = createSearchOnlyIntent();
            mockIntentExtraction(searchIntent);
            RAGResponse mockResponse = createMockRAGResponse();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            long startTime = System.currentTimeMillis();
            ragOrchestrator.orchestrate("Show me wallets under $60", TEST_USER_ID);
            long duration = System.currentTimeMillis() - startTime;

            // Assert: Should complete quickly (no LLM call)
            assertThat(duration).isLessThan(500);  // 500ms threshold
        }
    }

    @Nested
    @DisplayName("LLM Generation Query Tests")
    class LLMGenerationQueryTests {

        @Test
        @DisplayName("Should filter context to only include-in-rag: true fields")
        void testLLMGenerationFiltersContext() {
            // Arrange
            Intent genIntent = createLLMGenerationIntent();
            mockIntentExtraction(genIntent);
            RAGResponse mockResponse = createMockRAGResponse();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Should I buy this wallet?",
                TEST_USER_ID
            );

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            
            // Verify context was filtered
            assertThat(data).containsKey("contextFiltered");
            assertThat(data.get("contextFiltered")).isEqualTo(true);
            
            // Verify filtered context exists
            assertThat(data).containsKey("filteredContext");
            @SuppressWarnings("unchecked")
            Map<String, Object> filteredContext = (Map<String, Object>) data.get("filteredContext");
            
            // Verify filtered context contains only allowed fields
            @SuppressWarnings("unchecked")
            Map<String, Object> product = (Map<String, Object>) 
                filteredContext.values().iterator().next();
            
            // Should have: name, description, retailPrice
            assertThat(product).containsKeys("name", "description", "retailPrice");
            
            // Should NOT have: costPrice, internalMargin
            assertThat(product).doesNotContainKey("costPrice");
            assertThat(product).doesNotContainKey("internalMargin");
        }

        @Test
        @DisplayName("Should hide costPrice from LLM (include-in-rag: false)")
        void testCostPriceHiddenFromLLM() {
            // Arrange
            Intent genIntent = createLLMGenerationIntent();
            mockIntentExtraction(genIntent);
            RAGResponse mockResponse = createMockRAGResponse();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Should I buy this wallet?",
                TEST_USER_ID
            );

            // Assert
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> filteredContext = (Map<String, Object>) data.get("filteredContext");
            @SuppressWarnings("unchecked")
            Map<String, Object> product = (Map<String, Object>) 
                filteredContext.values().iterator().next();
            
            // Verify costPrice (include-in-rag: false) is excluded
            assertThat(product).doesNotContainKey("costPrice");
            assertThat(product.get("costPrice")).isNull();
        }

        @Test
        @DisplayName("Should include retailPrice in LLM context (include-in-rag: true by default)")
        void testRetailPriceIncludedInLLM() {
            // Arrange
            Intent genIntent = createLLMGenerationIntent();
            mockIntentExtraction(genIntent);
            RAGResponse mockResponse = createMockRAGResponse();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Should I buy this wallet?",
                TEST_USER_ID
            );

            // Assert
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> filteredContext = (Map<String, Object>) data.get("filteredContext");
            @SuppressWarnings("unchecked")
            Map<String, Object> product = (Map<String, Object>) 
                filteredContext.values().iterator().next();
            
            // Verify retailPrice is included (no include-in-rag: false config)
            assertThat(product).containsKey("retailPrice");
            assertThat(product.get("retailPrice")).isEqualTo(199.99);
        }

        @Test
        @DisplayName("Should handle LLM generation error gracefully")
        void testLLMGenerationErrorFallback() {
            // Arrange
            Intent genIntent = createLLMGenerationIntent();
            mockIntentExtraction(genIntent);
            RAGResponse mockResponse = createMockRAGResponse();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenThrow(new RuntimeException("LLM service unavailable"));

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Should I buy this wallet?",
                TEST_USER_ID
            );

            // Assert: Should still return success with search results
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            
            // Should have fallback reason
            assertThat(data).containsKey("fallbackReason");
            assertThat(data.get("fallbackReason")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Intent Detection Tests")
    class IntentDetectionTests {

        @Test
        @DisplayName("Should detect search-only intent from 'Show me' query")
        void testDetectSearchOnlyFromShowMe() {
            // Arrange
            Intent intent = Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("find_products")
                .build();
            intent.normalize();

            // Assert: Search-only by default
            assertThat(intent.requiresRetrievalOrDefault(false)).isTrue();
        }

        @Test
        @DisplayName("Should detect LLM generation intent from 'Should I' query")
        void testDetectLLMGenerationFromShouldI() {
            // Arrange
            Intent intent = Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("should_i_buy_this_product")
                .build();
            intent.normalize();

            // Assert: Should detect generation needed from keywords
            assertThat(intent.requiresGenerationOrDefault(false)).isTrue();
        }

        @Test
        @DisplayName("Should detect LLM generation from recommendation keywords")
        void testDetectLLMGenerationFromRecommend() {
            // Arrange
            Intent intent = Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("recommend_best_wallet")
                .build();
            intent.normalize();

            // Assert
            assertThat(intent.requiresGenerationOrDefault(false)).isTrue();
        }

        @Test
        @DisplayName("Should detect LLM generation from comparison keywords")
        void testDetectLLMGenerationFromCompare() {
            // Arrange
            Intent intent = Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("compare_wallet_options")
                .build();
            intent.normalize();

            // Assert
            assertThat(intent.requiresGenerationOrDefault(false)).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle missing entity configuration gracefully")
        void testMissingEntityConfiguration() {
            // Arrange
            when(configurationLoader.getEntityConfig("unknown-entity"))
                .thenReturn(null);
            
            RAGResponse mockResponse = createMockRAGResponse();
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act - should not throw exception
            assertThatNoException().isThrownBy(() ->
                ragOrchestrator.orchestrate("Show me unknown entities", TEST_USER_ID)
            );
        }

        @Test
        @DisplayName("Should handle null documents in response")
        void testNullDocumentsInResponse() {
            // Arrange
            Intent searchIntent = createSearchOnlyIntent();
            RAGResponse mockResponse = RAGResponse.builder()
                .response("No results found")
                .documents(null)
                .build();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Show me non-existent products",
                TEST_USER_ID
            );

            // Assert: Should handle gracefully
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should handle empty documents list")
        void testEmptyDocumentsList() {
            // Arrange
            Intent searchIntent = createSearchOnlyIntent();
            RAGResponse mockResponse = RAGResponse.builder()
                .response("No results found")
                .documents(Collections.emptyList())
                .build();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Show me products",
                TEST_USER_ID
            );

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should preserve unknown fields in LLM context")
        void testUnknownFieldsPreserved() {
            // Arrange
            Intent genIntent = createLLMGenerationIntent();
            mockIntentExtraction(genIntent);
            
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("name", "Wallet");
            metadata.put("unknownField", "some-value");
            
            RAGResponse mockResponse = RAGResponse.builder()
                .response("Found product")
                .documents(List.of(RAGResponse.RAGDocument.builder()
                    .id("prod-123")
                    .metadata(metadata)
                    .build()))
                .build();
            
            when(ragService.performRag(any(RAGRequest.class)))
                .thenReturn(mockResponse);

            // Act
            OrchestrationResult result = ragOrchestrator.orchestrate(
                "Should I buy this?",
                TEST_USER_ID
            );

            // Assert: Unknown fields should be included (fail-open)
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> filteredContext = (Map<String, Object>) data.get("filteredContext");
            @SuppressWarnings("unchecked")
            Map<String, Object> product = (Map<String, Object>) 
                filteredContext.values().iterator().next();
            
            assertThat(product).containsKey("unknownField");
        }
    }
}

