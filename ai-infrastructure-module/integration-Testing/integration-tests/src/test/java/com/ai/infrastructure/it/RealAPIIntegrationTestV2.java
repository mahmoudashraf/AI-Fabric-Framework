package com.ai.infrastructure.it;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.entity.V2AnnotatedProduct;
import com.ai.infrastructure.it.repository.V2AnnotatedProductRepository;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RealAPIIntegrationTest v2
 *
 * <p>Validates end-to-end indexing uses v2 field annotations:</p>
 * <ul>
 *   <li>{@code @AISearchable} controls what text is embedded/indexed</li>
 *   <li>{@code @AIContext} contributes metadata without affecting embedded text</li>
 * </ul>
 *
 * <p>This test intentionally uses fields that are NOT part of the YAML configuration for {@code test-product}
 * to prove the indexing path is annotation-driven when annotations are present.</p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPIIntegrationTestV2 {

    static {
        // Ensure providers are configured consistently with other real-api tests.
        RealAPITestSupport.ensureProviderConfigured();
        RealAPITestSupport.ensureLLMProviderSet();
        System.setProperty("EMBEDDING_PROVIDER",
            System.getProperty("EMBEDDING_PROVIDER", System.getenv("EMBEDDING_PROVIDER") != null ? System.getenv("EMBEDDING_PROVIDER") : "onnx"));
        System.setProperty("ai.providers.embedding-provider",
            System.getProperty("ai.providers.embedding-provider", "onnx"));
    }

    @Autowired
    private AICapabilityService capabilityService;

    @Autowired
    private AISearchableEntityStorageStrategy storageStrategy;

    @Autowired
    private VectorManagementService vectorManagementService;

    @Autowired
    private V2AnnotatedProductRepository v2ProductRepository;

    @BeforeEach
    public void setUp() {
        vectorManagementService.clearAllVectors();
        storageStrategy.deleteAll();
        v2ProductRepository.deleteAll();
    }

    @Test
    public void testV2AnnotationsControlIndexedSearchableContentAndMetadata() {
        // Given
        String v2SearchableOnly = "V2_SENTINEL_SEARCHABLE_ONLY";
        String v2ContextOwner = "v2-owner-123";

        V2AnnotatedProduct product = V2AnnotatedProduct.builder()
            .name("baseline-name")
            .internalNotes(v2SearchableOnly)
            .contextOwner(v2ContextOwner)
            .build();

        product = v2ProductRepository.save(product);

        // When: index using the existing pipeline entrypoint (uses entityType "test-product" config)
        capabilityService.processEntityForAI(product, "test-product");

        // Then
        List<AISearchableEntity> entities = storageStrategy.findByEntityType("test-product");
        assertThat(entities).hasSize(1);

        AISearchableEntity stored = entities.getFirst();

        // v2 requirement: the embedded/indexed searchable content is driven by @AISearchable, not YAML fields.
        assertThat(stored.getSearchableContent()).isEqualTo(v2SearchableOnly);

        // v2 requirement: @AIContext contributes metadata (without being embedded).
        assertThat(stored.getMetadata()).contains("\"owner\"");
        assertThat(stored.getMetadata()).contains(v2ContextOwner);
    }
}

