package com.ai.infrastructure.vector;

import com.ai.infrastructure.rag.VectorDatabaseService;

/**
 * Backwards-compatible wrapper that adapts {@link VectorDatabaseService} to the legacy
 * {@link VectorDatabase} interface for Pinecone deployments.
 *
 * @deprecated Prefer using {@link VectorDatabaseServiceAdapter} directly.
 */
@Deprecated(forRemoval = true)
public class PineconeVectorDatabase extends VectorDatabaseServiceAdapter {

    public PineconeVectorDatabase(VectorDatabaseService delegate) {
        super(delegate);
    }
}
