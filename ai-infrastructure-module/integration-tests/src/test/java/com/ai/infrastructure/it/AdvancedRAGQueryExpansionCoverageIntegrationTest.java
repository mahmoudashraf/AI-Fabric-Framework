package com.ai.infrastructure.it;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.AdvancedRAGResponse.RAGDocument;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.rag.AdvancedRAGService;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.service.VectorManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Integration test for TEST-RAG-005: Query Expansion.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "ai.vector-db.lucene.index-path=./data/test-lucene-index/query-expansion-coverage",
    "ai.vector-db.lucene.similarity-threshold=0.35"
})
class AdvancedRAGQueryExpansionCoverageIntegrationTest {

    private static final String ENTITY_TYPE = "ragproduct-expansion-coverage";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SpyBean
    private RAGService ragService;

    @Autowired
    private AdvancedRAGService advancedRAGService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AICoreService aiCoreService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            RAGRequest request = invocation.getArgument(0);
            request.setThreshold(0.0);
            return invocation.callRealMethod();
        }).when(ragService).performRag(any(RAGRequest.class));

        when(aiCoreService.generateText(anyString())).thenReturn(
            String.join("\n",
                "luxury watch",
                "collector chronograph",
                "modern jewelry pairing",
                "premium accessory ensemble"
            )
        );

        when(aiCoreService.generateContent(any(AIGenerationRequest.class))).thenReturn(
            AIGenerationResponse.builder()
                .content("Expanded overview covering premium watches, jewelry, and accessories.")
                .model("mock-openai")
                .build()
        );

        vectorManagementService.clearVectorsByEntityType(ENTITY_TYPE);
        seedCatalog();
    }

    @AfterEach
    void tearDown() {
        vectorManagementService.clearVectorsByEntityType(ENTITY_TYPE);
    }

    @Test
    @DisplayName("Query expansion broadens category coverage and pulls additional results")
    void queryExpansionImprovesRecall() {
        AdvancedRAGResponse baseline = advancedRAGService.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query("watch")
                .maxResults(12)
                .maxDocuments(6)
                .expansionLevel(0)
                .enableHybridSearch(true)
                .enableContextualSearch(false)
                .categories(List.of("watches"))
                .filters(Map.of("category", "watches"))
                .contextOptimizationLevel("low")
                .rerankingStrategy("score")
                .build()
        );

        AdvancedRAGResponse expanded = advancedRAGService.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query("watch")
                .maxResults(12)
                .maxDocuments(6)
                .expansionLevel(5)
                .enableHybridSearch(true)
                .enableContextualSearch(false)
                .categories(List.of("watches", "jewelry", "accessories"))
                .contextOptimizationLevel("medium")
                .rerankingStrategy("hybrid")
                .build()
        );

        assertNotNull(baseline, "Baseline response should not be null");
        assertNotNull(expanded, "Expanded response should not be null");
        assertTrue(Boolean.TRUE.equals(expanded.getSuccess()), "Expanded request should succeed");

        List<RAGDocument> baselineDocs = Optional.ofNullable(baseline.getDocuments()).orElse(List.of());
        List<RAGDocument> expandedDocs = Optional.ofNullable(expanded.getDocuments()).orElse(List.of());

        assertTrue(baselineDocs.isEmpty(), "Baseline search without expansion should miss relevant results");
        assertFalse(expandedDocs.isEmpty(), "Expanded search should return documents");
        assertTrue(expandedDocs.size() >= 10, "Expanded search should surface a richer document set");

        Set<String> expandedCategories = expandedDocs.stream()
            .map(doc -> ensureMetadata(doc).get("category"))
            .filter(value -> value instanceof String)
            .map(value -> ((String) value).toLowerCase())
            .collect(Collectors.toSet());

        Set<String> expandedBrands = expandedDocs.stream()
            .map(doc -> ensureMetadata(doc).get("brand"))
            .filter(Objects::nonNull)
            .map(Object::toString)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        assertFalse(expandedCategories.isEmpty(), "Expanded search should return documents with metadata");
        assertTrue(expandedBrands.size() >= 2, "Expanded search should surface multiple brands");

        List<String> expandedQueries = Optional.ofNullable(expanded.getExpandedQueries()).orElse(List.of());
        assertTrue(expandedQueries.stream().anyMatch(q -> q.toLowerCase().contains("chronograph")),
            "Expanded queries should include chronograph synonym");
        assertTrue(expandedQueries.stream().anyMatch(q -> q.toLowerCase().contains("jewelry")),
            "Expanded queries should include jewelry related term");
        assertTrue(expandedQueries.stream().anyMatch(q -> q.toLowerCase().contains("accessory")),
            "Expanded queries should include accessory related term");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureMetadata(RAGDocument document) {
        Object metadata = document.getMetadata();
        if (metadata instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (metadata instanceof String json) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, Map.class);
                document.setMetadata(parsed);
                return parsed;
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private void seedCatalog() {
        IntStream.range(0, 10).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_watch_" + index,
            String.format(
                "Luxury watch %1$d chronograph watch timepiece watch collector edition watch with precision movement and heritage design tailored for watch enthusiasts.",
                index
            ),
            Map.of(
                "category", "watches",
                "brand", index % 2 == 0 ? "Rolex" : "Omega",
                "priceRange", index % 3 == 0 ? "luxury" : "premium"
            )
        ));

        IntStream.range(0, 6).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_jewelry_" + index,
            String.format("Modern jewelry pairing %d featuring diamonds and precious metals for evening events.", index),
            Map.of(
                "category", "jewelry",
                "brand", index % 2 == 0 ? "Cartier" : "Tiffany",
                "priceRange", "premium"
            )
        ));

        IntStream.range(0, 4).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_accessory_" + index,
            String.format("Accessory ensemble %d curated to complete refined wardrobes for special occasions.", index),
            Map.of(
                "category", "jewelry",
                "brand", index % 2 == 0 ? "Cartier" : "Tiffany",
                "priceRange", "premium"
            )
        ));
    }
}

