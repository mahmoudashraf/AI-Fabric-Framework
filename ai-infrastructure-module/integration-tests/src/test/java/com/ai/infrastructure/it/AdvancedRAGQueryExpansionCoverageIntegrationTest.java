package com.ai.infrastructure.it;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.AdvancedRAGResponse.RAGDocument;
import com.ai.infrastructure.rag.AdvancedRAGService;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
import static org.mockito.Mockito.when;

/**
 * Integration test for TEST-RAG-005: Query Expansion.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = "ai.vector-db.lucene.index-path=./data/test-lucene-index/query-expansion-coverage")
class AdvancedRAGQueryExpansionCoverageIntegrationTest {

    private static final String ENTITY_TYPE = "ragproduct-expansion-coverage";

    @Autowired
    private RAGService ragService;

    @Autowired
    private AdvancedRAGService advancedRAGService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AICoreService aiCoreService;

    @BeforeEach
    void setUp() {
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
                .maxResults(6)
                .maxDocuments(4)
                .expansionLevel(0)
                .enableHybridSearch(true)
                .enableContextualSearch(false)
                .categories(List.of("watches", "jewelry", "accessories"))
                .contextOptimizationLevel("low")
                .rerankingStrategy("score")
                .build()
        );

        AdvancedRAGResponse expanded = advancedRAGService.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query("watch")
                .maxResults(6)
                .maxDocuments(4)
                .expansionLevel(4)
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

        assertFalse(expandedDocs.isEmpty(), "Expanded search should return documents");
        assertTrue(expandedDocs.size() >= baselineDocs.size(),
            "Expanded results should be at least as many as baseline results");

        Set<String> baselineCategories = baselineDocs.stream()
            .map(RAGDocument::getMetadata)
            .filter(Objects::nonNull)
            .map(metadata -> (String) metadata.get("category"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<String> expandedCategories = expandedDocs.stream()
            .map(RAGDocument::getMetadata)
            .filter(Objects::nonNull)
            .map(metadata -> (String) metadata.get("category"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        assertTrue(expandedCategories.contains("watches"), "Expanded results should include watches");
        assertTrue(expandedCategories.contains("jewelry"), "Expanded results should include jewelry");
        assertTrue(expandedCategories.contains("accessories"), "Expanded results should include accessories");
        assertTrue(expandedCategories.size() >= baselineCategories.size(),
            "Expanded search should cover at least as many categories as baseline");

        List<String> expandedQueries = Optional.ofNullable(expanded.getExpandedQueries()).orElse(List.of());
        assertTrue(expandedQueries.stream().anyMatch(q -> q.toLowerCase().contains("chronograph")),
            "Expanded queries should include chronograph synonym");
        assertTrue(expandedQueries.stream().anyMatch(q -> q.toLowerCase().contains("jewelry")),
            "Expanded queries should include jewelry related term");
        assertTrue(expandedQueries.stream().anyMatch(q -> q.toLowerCase().contains("accessory")),
            "Expanded queries should include accessory related term");
    }

    private void seedCatalog() {
        IntStream.range(0, 10).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_watch_" + index,
            String.format("Luxury watch %d with precision chronograph movement and heritage design.", index),
            Map.of(
                "category", "watches",
                "brand", index % 2 == 0 ? "Rolex" : "Omega",
                "priceRange", index % 3 == 0 ? "luxury" : "premium"
            )
        ));

        IntStream.range(0, 6).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_jewelry_" + index,
            String.format("Modern jewelry pairing %d featuring diamonds and precious metals.", index),
            Map.of(
                "category", "jewelry",
                "brand", index % 2 == 0 ? "Cartier" : "Tiffany",
                "priceRange", "premium"
            )
        ));

        IntStream.range(0, 4).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_accessory_" + index,
            String.format("Accessory ensemble %d designed to complement luxury timepieces.", index),
            Map.of(
                "category", "accessories",
                "brand", index % 2 == 0 ? "Hermès" : "Gucci",
                "priceRange", "premium"
            )
        ));
    }
}

