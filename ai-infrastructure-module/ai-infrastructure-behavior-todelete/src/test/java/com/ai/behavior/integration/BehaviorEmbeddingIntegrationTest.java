package com.ai.behavior.integration;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorEmbedding;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.storage.BehaviorEmbeddingRepository;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BehaviorEmbeddingIntegrationTest extends BehaviorAnalyticsIntegrationTest {

    @Autowired
    private BehaviorIngestionService ingestionService;

    @Autowired
    private BehaviorEmbeddingRepository embeddingRepository;

    @Autowired
    private AICoreService aiCoreService;

    @Autowired
    private BehaviorModuleProperties properties;

    @BeforeEach
    void configureEmbeddingPipeline() {
        embeddingRepository.deleteAll();

        properties.getProcessing().getEmbedding().setEnabled(true);
        properties.getProcessing().getEmbedding().setSchemaIds(List.of("intent.search"));

        reset(aiCoreService);
        when(aiCoreService.generateEmbedding(any(AIEmbeddingRequest.class))).thenReturn(
            AIEmbeddingResponse.builder()
                .embedding(List.of(0.05d, 0.25d, 0.5d))
                .model("mock-openai-embed")
                .dimensions(3)
                .build()
        );
    }

    @Test
    void ingestingSearchSignalShouldPersistEmbeddingAndInvokeAICore() {
        BehaviorSignal signal = BehaviorSignal.builder()
            .schemaId("intent.search")
            .userId(UUID.randomUUID())
            .timestamp(LocalDateTime.now())
            .attributes(new java.util.HashMap<>(Map.of("query", "Find premium workspace plans for enterprise")))
            .build();

        ingestionService.ingest(signal);

        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(embeddingRepository.findAll()).hasSize(1));

        BehaviorEmbedding embedding = embeddingRepository.findAll().get(0);
        assertThat(embedding.getOriginalText()).contains("premium workspace plans");
        assertThat(embedding.getEmbedding()).containsExactly(0.05d, 0.25d, 0.5d);
        assertThat(embedding.getModel()).isEqualTo("mock-openai-embed");

        verify(aiCoreService, atLeastOnce()).generateEmbedding(argThat(request ->
            "behavior_signal".equals(request.getEntityType())
                && request.getEntityId().equals(signal.getId().toString())
                && request.getText().contains("workspace plans")
        ));
    }

    @Test
    void shortQueriesShouldSkipEmbeddingGeneration() {
        BehaviorSignal signal = BehaviorSignal.builder()
            .schemaId("intent.search")
            .userId(UUID.randomUUID())
            .timestamp(LocalDateTime.now())
            .attributes(new java.util.HashMap<>(Map.of("query", "hi")))
            .build();

        ingestionService.ingest(signal);

        Awaitility.await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(embeddingRepository.findAll()).isEmpty());

        verify(aiCoreService, never()).generateEmbedding(any(AIEmbeddingRequest.class));
    }
}
