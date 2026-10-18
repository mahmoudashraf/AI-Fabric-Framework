package com.ai.fabric.runtime.budget;

import ai.fabric.core.AIEmbeddingService;
import ai.fabric.indexing.api.AIIndexAnalysisHandler;
import ai.fabric.indexing.model.AIIndexDocument;
import ai.fabric.indexing.worker.IndexingExecutionException;
import ai.fabric.indexing.worker.IndexingOperationExecutor;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.service.VectorManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;

/** Worker-thread scope plus a pre-vector-write boundary; never assumes vector writes can be rolled back. */
public final class RuntimeBudgetIndexingOperationExecutor extends IndexingOperationExecutor {
    private final RuntimeBudgetTurnScopes turns;

    public RuntimeBudgetIndexingOperationExecutor(AIEmbeddingService embeddings, VectorManagementService vectors,
        VectorDatabaseService vectorDatabase, ObjectProvider<AIIndexAnalysisHandler> analysis, ObjectMapper mapper,
        RuntimeBudgetTurnScopes turns) {
        super(embeddings, new BeforeVectorWrite(vectors, vectorDatabase, turns), analysis, mapper);
        this.turns = turns;
    }

    @Override
    public String execute(AIIndexDocument document, long workId) {
        return switch (document.workType()) {
            case DELETE -> super.execute(document, workId);
            case ANALYZE -> throw new IndexingExecutionException("PROVIDER_BUDGET_ANALYZE_UNSUPPORTED");
            case UPSERT -> {
                try (var work = turns.openIndexingWork()) {
                    try {
                        String result = super.execute(document, workId);
                        work.verifyComplete();
                        yield result;
                    } catch (RuntimeException failure) {
                        if (work.interruption().isPresent()) throw new IndexingExecutionException(RuntimeBudgetTurnScopes.INTERRUPTION_CODE);
                        throw failure;
                    }
                }
            }
        };
    }

    private static final class BeforeVectorWrite extends VectorManagementService {
        private final VectorManagementService delegate;
        private final RuntimeBudgetTurnScopes turns;
        BeforeVectorWrite(VectorManagementService delegate, VectorDatabaseService vectorDatabase, RuntimeBudgetTurnScopes turns) {
            super(vectorDatabase);
            this.delegate = delegate; this.turns = turns;
        }
        @Override public String storeVector(String type, String id, String content, List<Double> embedding, Map<String, Object> metadata) {
            turns.verifyIndexingBeforeVectorWrite();
            return delegate.storeVector(type, id, content, embedding, metadata);
        }
        @Override public String updateVector(String type, String id, String content, List<Double> embedding, Map<String, Object> metadata) {
            turns.verifyIndexingBeforeVectorWrite();
            return delegate.updateVector(type, id, content, embedding, metadata);
        }
        @Override public boolean removeVector(String type, String id) { return delegate.removeVector(type, id); }
    }
}
