package com.ai.infrastructure.intent.orchestration;

/**
 * Centralized keys for {@link OrchestrationContext#getMetadata()}.
 *
 * <p>These keys are used internally by the orchestrator and/or by applications when constructing
 * an {@link OrchestrationContext}. Avoid scattering string literals across the codebase.</p>
 */
public final class OrchestrationContextMetadataKeys {

    private OrchestrationContextMetadataKeys() {
    }

    /**
     * When true, Advanced RAG is explicitly enabled for the request (when a provider is present).
     */
    public static final String USE_ADVANCED_RAG = "useAdvancedRAG";
}

