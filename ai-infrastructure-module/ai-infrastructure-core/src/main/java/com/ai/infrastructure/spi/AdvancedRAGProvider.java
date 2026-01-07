package com.ai.infrastructure.spi;

import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;

/**
 * Service Provider Interface for advanced RAG operations.
 *
 * <p>This SPI allows the orchestration pipeline (core module) to optionally invoke
 * advanced retrieval pipelines (query expansion, re-ranking, context optimization)
 * without creating a compile-time dependency on a concrete implementation module.</p>
 *
 * <p><strong>Module Boundary:</strong></p>
 * <ul>
 *   <li>The core module depends only on this interface and the DTOs.</li>
 *   <li>The RAG module provides an implementation (e.g., {@code AdvancedRAGService}).</li>
 *   <li>Applications may provide custom implementations.</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> Implementations MUST be thread-safe.</p>
 *
 * @since 1.0
 */
public interface AdvancedRAGProvider {

    /**
     * Perform advanced retrieval-augmented generation.
     *
     * <p>Implementations may perform LLM calls for query expansion and context optimization and
     * may also generate a final answer text, depending on the implementation.</p>
     *
     * @param request advanced RAG request configuration
     * @return advanced RAG response; never null
     */
    AdvancedRAGResponse performAdvancedRAG(AdvancedRAGRequest request);
}

