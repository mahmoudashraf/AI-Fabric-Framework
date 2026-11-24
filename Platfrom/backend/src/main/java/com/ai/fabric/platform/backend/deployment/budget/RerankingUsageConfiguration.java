package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.service.KnowledgeRetrievalSettings;
import com.fasterxml.jackson.databind.JsonNode;

final class RerankingUsageConfiguration {
    private RerankingUsageConfiguration() { }

    static boolean enabled(String json, ProviderUsageAllowanceCodec codec) {
        return KnowledgeRetrievalSettings.rerankingEnabled(codec.read(json, JsonNode.class));
    }

    static void require(String json, ProviderUsageAllowanceCodec codec, ProviderUsageAllowanceContracts.ProviderBinding binding) {
        JsonNode provider = codec.read(json, JsonNode.class);
        KnowledgeRetrievalSettings.runtimeEnvironment(provider);
        JsonNode reranking = provider.path("knowledgeRetrieval").path("reranking");
        if (!KnowledgeRetrievalSettings.rerankingEnabled(provider) || !binding.provider().equals(reranking.path("provider").asText())
            || !binding.modelIds().contains(reranking.path("model").asText())) {
            throw new IllegalStateException("RERANKING_USAGE_PUBLISHED_CONFIGURATION_MISMATCH");
        }
    }
}
