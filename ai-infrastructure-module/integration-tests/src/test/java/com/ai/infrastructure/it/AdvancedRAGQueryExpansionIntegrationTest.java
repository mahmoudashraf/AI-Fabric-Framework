package com.ai.infrastructure.it;

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

/**
 * Integration test implementation for TEST-RAG-005: Query Expansion.
 *
 * <p>This test verifies that AdvancedRAGService uses OpenAI-powered query expansion
 * while the underlying embeddings continue to run on the ONNX provider configured
 * for the dev profile. The expanded query should broaden the set of retrieved
 * documents and surface multiple categories relevant to the user intent.</p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class AdvancedRAGQueryExpansionIntegrationTest {

    private static final String ENTITY_TYPE = "ragproduct-expansion";

    @Autowired
    private RAGService ragService;

    @Autowired
    private AdvancedRAGService advancedRAGService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @BeforeEach
    void setUp() {
        vectorManagementService.clearVectorsByEntityType(ENTITY_TYPE);
        seedProductCatalog();
    }

    @AfterEach
    void tearDown() {
        vectorManagementService.clearVectorsByEntityType(ENTITY_TYPE);
    }

    @Test
    @DisplayName("Advanced RAG query expansion via OpenAI improves coverage with ONNX embeddings")
    void queryExpansionUsesOpenAIAndImprovesCoverage() {
        String baseQuery = "luxury timepiece";

        AdvancedRAGResponse baseline = advancedRAGService.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query(baseQuery)
                .expansionLevel(1)
                .enableHybridSearch(false)
                .enableContextualSearch(false)
                .categories(List.of("watches"))
                .maxResults(5)
                .maxDocuments(5)
                .rerankingStrategy("score")
                .contextOptimizationLevel("low")
                .build()
        );

        AdvancedRAGResponse expanded = advancedRAGService.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query(baseQuery)
                .expansionLevel(4)
                .enableHybridSearch(true)
                .enableContextualSearch(true)
                .categories(List.of("watches", "jewelry", "accessories"))
                .maxResults(10)
                .maxDocuments(6)
                .rerankingStrategy("semantic")
                .contextOptimizationLevel("high")
                .build()
        );

        assertNotNull(baseline, "Baseline response should not be null");
        assertTrue(Boolean.TRUE.equals(baseline.getSuccess()), "Baseline request should succeed");

        assertNotNull(expanded, "Expanded response should not be null");
        assertTrue(Boolean.TRUE.equals(expanded.getSuccess()), "Expanded request should succeed");

        List<String> expandedQueries = Optional.ofNullable(expanded.getExpandedQueries()).orElse(List.of());
        long distinctExpandedQueries = expandedQueries.stream().distinct().count();
        assertTrue(distinctExpandedQueries > 1, "Expanded query set should include OpenAI-provided variations");

        List<RAGDocument> baselineDocuments = Optional.ofNullable(baseline.getDocuments()).orElse(List.of());
        List<RAGDocument> expandedDocuments = Optional.ofNullable(expanded.getDocuments()).orElse(List.of());

        Set<String> baselineIds = baselineDocuments.stream()
            .map(RAGDocument::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<String> expandedIds = expandedDocuments.stream()
            .map(RAGDocument::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        assertFalse(expandedIds.isEmpty(), "Expanded query should return documents");
        assertTrue(expandedIds.size() >= baselineIds.size(),
            () -> "Expanded query should cover at least the baseline document count. Baseline=" + baselineIds.size()
                + ", Expanded=" + expandedIds.size());

        Set<String> expandedCategories = expandedDocuments.stream()
            .map(RAGDocument::getMetadata)
            .filter(Objects::nonNull)
            .map(metadata -> metadata.get("category"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .collect(Collectors.toSet());

        assertTrue(expandedCategories.size() >= 2,
            () -> "Expanded query should surface multiple categories, but found: " + expandedCategories);

        long processingTimeMs = Optional.ofNullable(expanded.getProcessingTimeMs()).orElse(0L);
        assertTrue(processingTimeMs > 0,
            () -> "OpenAI-assisted expansion should report processing time, but was " + processingTimeMs + " ms");
    }

    private void seedProductCatalog() {
        List<String> categories = List.of("watches", "jewelry", "accessories");

        IntStream.range(0, 30)
            .mapToObj(index -> buildProductDocument(index, categories.get(index % categories.size())))
            .forEach(document -> ragService.indexContent(ENTITY_TYPE, document.id(), document.content(), document.metadata()));
    }

    private ProductDocument buildProductDocument(int index, String category) {
        String brand = switch (index % 3) {
            case 0 -> "Rolex";
            case 1 -> "Omega";
            default -> "Cartier";
        };

        String descriptor = switch (category) {
            case "watches" -> "chronograph timepiece crafted for horology enthusiasts";
            case "jewelry" -> "diamond bracelet inspired by haute horlogerie motifs";
            default -> "luxury accessory pairing with prestige chronographs";
        };

        String content = String.format(
            "Luxury %s %s collection %d featuring %s and artisan craftsmanship for collectors.",
            category,
            brand,
            index,
            descriptor
        );

        Map<String, Object> metadata = Map.of(
            "category", category,
            "brand", brand,
            "priceRange", index % 2 == 0 ? "premium" : "ultra-premium",
            "style", index % 2 == 0 ? "classic" : "avant-garde"
        );

        String documentId = String.format("%s_%s_%d", ENTITY_TYPE, category, index);
        return new ProductDocument(documentId, content, metadata);
    }

    private record ProductDocument(String id, String content, Map<String, Object> metadata) { }
}

