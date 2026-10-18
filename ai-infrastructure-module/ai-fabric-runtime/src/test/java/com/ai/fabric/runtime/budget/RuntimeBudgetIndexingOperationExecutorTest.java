package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.core.AIEmbeddingService;
import ai.fabric.dto.AIEmbeddingResponse;
import ai.fabric.embedding.EmbeddingProvider;
import ai.fabric.indexing.api.AIIndexAnalysisHandler;
import ai.fabric.indexing.api.AIIndexWorkType;
import ai.fabric.indexing.api.AIProcessOperation;
import ai.fabric.indexing.model.AIIndexDocument;
import ai.fabric.indexing.worker.IndexingExecutionException;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.service.VectorManagementService;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class RuntimeBudgetIndexingOperationExecutorTest extends RuntimeBudgetFixture.TestScope {
    @Test
    void realEmbeddingServicePropagatesObservedBudgetDenialBeforeAnyVectorPersistence() throws Exception {
        var turns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().register());
        var provider = mock(EmbeddingProvider.class);
        when(provider.getProviderName()).thenReturn("openai");
        when(provider.isAvailable()).thenReturn(true);
        when(provider.generateEmbedding(any())).thenAnswer(invocation -> {
            deny(turns);
            throw new BudgetException(BudgetException.Code.LIMIT_EXCEEDED);
        });
        var config = new AIProviderConfig();
        config.setEmbeddingProvider("openai"); config.setEnableFallback(false);
        var realEmbeddings = new AIEmbeddingService(config, provider, null, null);
        var vectors = mock(VectorManagementService.class);
        var executor = executor(realEmbeddings, vectors, turns);
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> assertThatThrownBy(() -> executor.execute(document(AIIndexWorkType.UPSERT), 1))
                .isInstanceOfSatisfying(IndexingExecutionException.class,
                    failure -> assertThat(failure.getErrorCode()).isEqualTo(RuntimeBudgetTurnScopes.INTERRUPTION_CODE)))
                .get(5, TimeUnit.SECONDS);
        }
        verifyNoInteractions(vectors);
    }

    @Test
    void evenASwallowedDenialReturningAValidEmbeddingIsBlockedBeforeTheVectorWrite() throws Exception {
        var turns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().register());
        var embeddings = mock(AIEmbeddingService.class);
        when(embeddings.generateEmbedding(any())).thenAnswer(invocation -> {
            deny(turns);
            return AIEmbeddingResponse.builder().embedding(List.of(0.1, 0.2)).build();
        });
        var vectors = mock(VectorManagementService.class);
        assertThatThrownBy(() -> executor(embeddings, vectors, turns).execute(document(AIIndexWorkType.UPSERT), 2))
            .isInstanceOfSatisfying(IndexingExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(RuntimeBudgetTurnScopes.INTERRUPTION_CODE));
        verifyNoInteractions(vectors);
    }

    @Test
    void successfulUpsertPreservesFrameworkMetadataAndDeleteRequiresNoPaidAdmission() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var turns = new RuntimeBudgetTurnScopes(fixture.register());
        var embeddings = mock(AIEmbeddingService.class);
        when(embeddings.generateEmbedding(any())).thenReturn(AIEmbeddingResponse.builder().embedding(List.of(0.1, 0.2)).build());
        var vectors = mock(VectorManagementService.class);
        when(vectors.storeVector(anyString(), anyString(), anyString(), anyList(), anyMap())).thenReturn("vector-1");
        executor(embeddings, vectors, turns).execute(document(AIIndexWorkType.UPSERT), 17);
        verify(vectors).storeVector(eq("document"), eq("id"), eq("facts"), eq(List.of(0.1, 0.2)),
            argThat(metadata -> metadata.get("_aifabricWorkId").equals(17L)));
        var inactiveTurns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().service());
        executor(embeddings, vectors, inactiveTurns).execute(document(AIIndexWorkType.DELETE), 18);
        verify(vectors).removeVector("document", "id");
    }

    @Test
    void analyzeIsExplicitlyUnsupportedBeforeCallingAnyHandlerOrProvider() throws Exception {
        var embeddings = mock(AIEmbeddingService.class);
        var vectors = mock(VectorManagementService.class);
        var turns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().register());
        assertThatThrownBy(() -> executor(embeddings, vectors, turns).execute(document(AIIndexWorkType.ANALYZE), 19))
            .isInstanceOfSatisfying(IndexingExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("PROVIDER_BUDGET_ANALYZE_UNSUPPORTED"));
        verifyNoInteractions(embeddings, vectors);
    }

    private static void deny(RuntimeBudgetTurnScopes turns) {
        var captured = turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING);
        captured.observer().onDenied(new OpenAiBudgetInvocation.Denial(captured.operationId(), UUID.randomUUID(),
            BudgetException.Code.LIMIT_EXCEEDED, null));
    }

    private static RuntimeBudgetIndexingOperationExecutor executor(AIEmbeddingService embeddings, VectorManagementService vectors,
        RuntimeBudgetTurnScopes turns) {
        return new RuntimeBudgetIndexingOperationExecutor(embeddings, vectors, mock(VectorDatabaseService.class),
            new StaticListableBeanFactory().getBeanProvider(AIIndexAnalysisHandler.class), new ObjectMapper(), turns);
    }

    private static AIIndexDocument document(AIIndexWorkType type) {
        boolean delete = type == AIIndexWorkType.DELETE;
        return new AIIndexDocument(1, "a".repeat(64), "document", "id", type,
            delete ? AIProcessOperation.DELETE : AIProcessOperation.CREATE, delete ? null : "facts", null,
            Map.of(), Map.of(), Map.of(), null, "work", NOW);
    }
}
