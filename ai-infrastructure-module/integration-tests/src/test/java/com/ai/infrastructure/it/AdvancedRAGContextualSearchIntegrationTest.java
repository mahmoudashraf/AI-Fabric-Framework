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
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test implementation for TEST-RAG-004: Contextual Search with User Preferences.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = "ai.vector-db.lucene.index-path=./data/test-lucene-index/contextual")
class AdvancedRAGContextualSearchIntegrationTest {

    private static final String ENTITY_TYPE = "ragproduct-contextual";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private RAGService ragService;

    @Autowired
    private AdvancedRAGService advancedRAGService;

    @Autowired
    private VectorManagementService vectorManagementService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AICoreService aiCoreService;

    @BeforeEach
    void setUp() throws Exception {
        when(aiCoreService.generateText(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            if (prompt != null && prompt.toLowerCase().contains("optimize")) {
                return "Refined context emphasising premium modern watch pieces aligned with the user's style.";
            }
            return "modern premium watch recommendations\nsmart luxury watch combinations\ncontemporary collector watch guide";
        });

        when(aiCoreService.generateContent(any(AIGenerationRequest.class))).thenReturn(
            AIGenerationResponse.builder()
                .content("Personalized summary highlighting modern premium watches tailored to the user preferences.")
                .model("mock-openai")
                .build()
        );

        vectorManagementService.clearVectorsByEntityType(ENTITY_TYPE);
        seedLifestyleCatalog();
    }

    @AfterEach
    void tearDown() {
        vectorManagementService.clearVectorsByEntityType(ENTITY_TYPE);
    }

    @Test
    @DisplayName("Contextual search honours user preferences for luxury watch shopper")
    void contextualPreferencesDrivePersonalizedResults() throws Exception {
        String shopperQuery = "I'm shopping for a special occasion and need luxury accessories that match my style.";

        AdvancedRAGResponse broadResponse = advancedRAGService.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query(shopperQuery)
                .maxResults(12)
                .maxDocuments(8)
                .expansionLevel(3)
                .enableHybridSearch(true)
                .enableContextualSearch(true)
                .categories(List.of("watches", "jewelry", "accessories", "handbags"))
                .contextOptimizationLevel("medium")
                .rerankingStrategy("hybrid")
                .build()
        );

        assertNotNull(broadResponse, "Broad response should not be null");
        assertTrue(Boolean.TRUE.equals(broadResponse.getSuccess()), "Broad response should succeed");

        List<RAGDocument> broadDocuments = Optional.ofNullable(broadResponse.getDocuments()).orElse(List.of());
        assertFalse(broadDocuments.isEmpty(), "Broad response should include documents");

        Set<String> broadCategories = broadDocuments.stream()
            .map(RAGDocument::getMetadata)
            .filter(Objects::nonNull)
            .map(metadata -> (String) metadata.get("category"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        assertTrue(broadCategories.size() > 1, "Broad response should span multiple categories");

        Map<String, Object> userPreferences = Map.of(
            "preferredCategories", List.of("watches"),
            "priceRange", Map.of("min", 5000, "max", 15000),
            "style", "modern",
            "audience", "collectors"
        );

        String userContext = OBJECT_MAPPER.writeValueAsString(userPreferences);

        Map<String, Object> filters = Map.of(
            "category", "watches",
            "priceRange", "premium",
            "style", "modern"
        );

        AdvancedRAGResponse personalized = advancedRAGService.performAdvancedRAG(
            AdvancedRAGRequest.builder()
                .query(shopperQuery)
                .maxResults(10)
                .maxDocuments(6)
                .expansionLevel(3)
                .enableHybridSearch(true)
                .enableContextualSearch(true)
                .categories(List.of("watches", "jewelry"))
                .filters(filters)
                .context(userContext)
                .metadata(Map.of("userId", "vip-collector-42"))
                .contextOptimizationLevel("high")
                .rerankingStrategy("semantic")
                .build()
        );

        assertNotNull(personalized, "Personalized response should not be null");
        assertTrue(Boolean.TRUE.equals(personalized.getSuccess()), "Personalized response should succeed");

        List<RAGDocument> personalizedDocs = Optional.ofNullable(personalized.getDocuments()).orElse(List.of());
        assertFalse(personalizedDocs.isEmpty(), "Personalized response should not be empty");

        personalizedDocs.forEach(doc -> {
            Map<String, Object> metadata = doc.getMetadata();
            assertNotNull(metadata, "Metadata should be present for personalized document");
            assertTrue("watches".equalsIgnoreCase(String.valueOf(metadata.get("category"))),
                () -> "Expected category 'watches' but was " + metadata.get("category"));
            assertTrue("premium".equalsIgnoreCase(String.valueOf(metadata.get("priceRange"))),
                () -> "Expected priceRange 'premium' but was " + metadata.get("priceRange"));
            assertTrue("modern".equalsIgnoreCase(String.valueOf(metadata.get("style"))),
                () -> "Expected style 'modern' but was " + metadata.get("style"));
        });

        Set<String> personalizedIds = personalizedDocs.stream()
            .map(RAGDocument::getId)
            .collect(Collectors.toSet());
        assertTrue(personalizedIds.size() >= 3, "Personalized response should contain at least three unique watches");

        Set<String> personalizedCategories = personalizedDocs.stream()
            .map(RAGDocument::getMetadata)
            .filter(Objects::nonNull)
            .map(metadata -> (String) metadata.get("category"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        assertEquals(Set.of("watches"), personalizedCategories,
            () -> "Personalized response should be limited to watches but had " + personalizedCategories);

        String optimizedContext = personalized.getContext();
        assertNotNull(optimizedContext, "Personalized context should not be null");
        assertTrue(optimizedContext.toLowerCase().contains("modern"), "Context should reflect the user's style preference");
        assertTrue(optimizedContext.length() < 12_000, "Context should remain within the optimisation budget");

        assertTrue(personalizedDocs.size() < broadDocuments.size(),
            "Personalized response should be narrower than broad response");
    }

    private void seedLifestyleCatalog() {
        List<String> watchBrands = List.of("Rolex", "Omega", "Patek Philippe", "Grand Seiko");
        List<String> jewelryBrands = List.of("Cartier", "Tiffany", "Bvlgari");
        List<String> accessoryBrands = List.of("Hermès", "Prada", "Gucci");

        IntStream.range(0, 16).forEach(index -> {
            String brand = watchBrands.get(index % watchBrands.size());
            String style = index % 2 == 0 ? "modern" : "classic";
            String audience = index % 3 == 0 ? "collectors" : "enthusiasts";
            String priceRange = index % 2 == 0 ? "premium" : "luxury";

            ragService.indexContent(
                ENTITY_TYPE,
                ENTITY_TYPE + "_watch_" + index,
                String.format("%s modern chronograph %d with premium finishing for discerning %s.", brand, index, audience),
                Map.of(
                    "category", "watches",
                    "brand", brand,
                    "priceRange", priceRange,
                    "style", style,
                    "audience", audience
                )
            );
        });

        IntStream.range(0, 8).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_jewelry_" + index,
            String.format("%s signature bracelet %d crafted for evening events and luxury gifting.",
                jewelryBrands.get(index % jewelryBrands.size()), index),
            Map.of(
                "category", "jewelry",
                "brand", jewelryBrands.get(index % jewelryBrands.size()),
                "priceRange", index % 2 == 0 ? "luxury" : "premium",
                "style", index % 2 == 0 ? "classic" : "avant-garde"
            )
        ));

        IntStream.range(0, 6).forEach(index -> ragService.indexContent(
            ENTITY_TYPE,
            ENTITY_TYPE + "_accessory_" + index,
            String.format("%s accessory look %d to complement refined wardrobes.",
                accessoryBrands.get(index % accessoryBrands.size()), index),
            Map.of(
                "category", "accessories",
                "brand", accessoryBrands.get(index % accessoryBrands.size()),
                "priceRange", "premium",
                "style", index % 2 == 0 ? "modern" : "classic"
            )
        ));
    }
}

