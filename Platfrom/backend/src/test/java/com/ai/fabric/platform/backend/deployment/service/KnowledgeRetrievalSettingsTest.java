package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeRetrievalSettingsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void absencePreservesExistingRuntimeAndUnknownValuesAreUntouched() throws Exception {
        var provider = mapper.readTree("{\"vectorStrategy\":\"milvus\",\"custom\":42}");
        assertThat(KnowledgeRetrievalSettings.runtimeEnvironment(provider)).isEmpty();
        assertThat(provider.path("custom").asInt()).isEqualTo(42);
    }

    @Test
    void compilesActualFrameworkPrefixAndFiniteDefaults() throws Exception {
        var provider = mapper.readTree("{\"vectorStrategy\":\"weaviate\",\"knowledgeRetrieval\":{\"mode\":\"HYBRID\"}}");
        var env = KnowledgeRetrievalSettings.runtimeEnvironment(provider);
        assertThat(env).containsEntry("AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_MODE", "HYBRID")
            .containsEntry("AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_VECTORCANDIDATES", "20")
            .containsEntry("AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_FAILUREPOLICY", "FAIL");
    }

    @Test
    void cannotEnablePretendHybridOrUnboundedWork() throws Exception {
        var provider = mapper.readTree("{\"vectorStrategy\":\"milvus\",\"knowledgeRetrieval\":{\"mode\":\"HYBRID\"}}");
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider)).hasMessageContaining("not implemented");
        ((com.fasterxml.jackson.databind.node.ObjectNode) provider).put("vectorStrategy", "weaviate");
        var profile = (com.fasterxml.jackson.databind.node.ObjectNode) provider.path("knowledgeRetrieval");
        profile.put("vectorCandidates", 101);
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider)).hasMessageContaining("vectorCandidates");
        profile.put("vectorCandidates", 20).put("maxCandidates", 2).put("maxResults", 3);
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider)).hasMessageContaining("maxResults");
    }

    @Test void disabledProfileRetainsCustomSettingsWithoutEnablingAnyProvider() throws Exception {
        var provider = mapper.readTree("""
            {"knowledgeRetrieval":{"enabled":false,"mode":"HYBRID","futureSetting":42,
              "reranking":{"enabled":true,"provider":"cohere","model":"saved-model"}}}
            """);
        assertThat(KnowledgeRetrievalSettings.runtimeEnvironment(provider)).isEmpty();
        assertThat(KnowledgeRetrievalSettings.rerankingEnabled(provider)).isFalse();
        assertThat(provider.at("/knowledgeRetrieval/futureSetting").intValue()).isEqualTo(42);
        assertThat(provider.at("/knowledgeRetrieval/reranking/model").textValue()).isEqualTo("saved-model");
    }

    @Test void externalRecipientConsentAndReviewedModelAreRequired() throws Exception {
        var provider = mapper.readTree("""
            {"knowledgeRetrieval":{"mode":"DENSE","reranking":{
              "enabled":true,"provider":"cohere","model":"rerank-v5.0-fast"}}}
            """);
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider)).hasMessageContaining("Approve sending");
        var ranking = (com.fasterxml.jackson.databind.node.ObjectNode) provider.at("/knowledgeRetrieval/reranking");
        ranking.put("externalProcessingApproved", true);
        assertThat(KnowledgeRetrievalSettings.runtimeEnvironment(provider))
            .containsEntry("AI_INFRASTRUCTURE_RAG_RERANKING_MODEL", "rerank-v5.0-fast")
            .containsEntry("AI_INFRASTRUCTURE_RAG_RERANKING_MAXCANDIDATES", "40");
        ranking.put("model", "unreviewed-model");
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider)).hasMessageContaining("supported reranking");
    }

    @Test void rankingWorkAndPayloadBoundsAreEnforcedBeforeProvisioning() throws Exception {
        var provider = mapper.readTree("""
            {"knowledgeRetrieval":{"mode":"DENSE","reranking":{"enabled":true,"provider":"cohere",
              "model":"rerank-v5.0-pro","externalProcessingApproved":true}}}
            """);
        var ranking = (com.fasterxml.jackson.databind.node.ObjectNode) provider.at("/knowledgeRetrieval/reranking");
        for (var limit : java.util.Map.of("maxCandidates", 101, "maxCharactersPerCandidate", 16001,
            "maxTotalCharacters", 200001, "maxTokensPerDocument", 4097, "timeoutMs", 30001).entrySet()) {
            ranking.put(limit.getKey(), limit.getValue());
            assertThatThrownBy(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider)).hasMessageContaining(limit.getKey());
            ranking.remove(limit.getKey());
        }
        ranking.put("enabled", false).put("model", "saved-custom-model");
        assertThat(KnowledgeRetrievalSettings.runtimeEnvironment(provider))
            .containsEntry("AI_INFRASTRUCTURE_RAG_RERANKING_ENABLED", "false");
    }

    @Test void missingBudgetsInheritTheBrainsExistingPromptLimitsWithoutShrinkingThem() throws Exception {
        var provider = mapper.readTree("{\"knowledgeRetrieval\":{\"mode\":\"DENSE\"}}");
        var prompts = mapper.readTree("{\"ragMaxDocumentsUsedForContext\":8,\"ragMaxContextChars\":32000}");
        assertThat(KnowledgeRetrievalSettings.runtimeEnvironment(provider, prompts))
            .containsEntry("AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_MAXRESULTS", "8")
            .containsEntry("AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_MAXCONTEXTCHARACTERS", "32000");
        var profile = (com.fasterxml.jackson.databind.node.ObjectNode)provider.path("knowledgeRetrieval");
        profile.put("maxResults", 3).put("maxContextCharacters", 4000);
        assertThat(KnowledgeRetrievalSettings.runtimeEnvironment(provider, prompts))
            .containsEntry("AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_MAXRESULTS", "3")
            .containsEntry("AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_MAXCONTEXTCHARACTERS", "4000");
        ((com.fasterxml.jackson.databind.node.ObjectNode)prompts).put("ragMaxContextChars", 500000);
        assertThatCode(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider, prompts)).doesNotThrowAnyException();
        profile.remove("maxContextCharacters");
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.runtimeEnvironment(provider, prompts)).hasMessageContaining("ragMaxContextChars");
    }

    @Test void runtimeMustAttestSearchExecutionAndRerankingAccountingBeforeApply() throws Exception {
        var provider = mapper.readTree("{\"knowledgeRetrieval\":{\"mode\":\"DENSE\"}}");
        assertThat(KnowledgeRetrievalSettings.profileEnabled(provider)).isTrue();
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.requireRuntimeSupport(provider, mapper.readTree("{}")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("shared-profiled-retrieval-v1");
        var manifest = mapper.readTree("{\"capabilities\":[\"shared-profiled-retrieval-v1\"]}");
        assertThatCode(() -> KnowledgeRetrievalSettings.requireRuntimeSupport(provider, manifest)).doesNotThrowAnyException();
        ((com.fasterxml.jackson.databind.node.ObjectNode)provider.path("knowledgeRetrieval")).putObject("reranking").put("enabled", true);
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.requireRuntimeSupport(provider, manifest)).hasMessageContaining("learned-reranking-v1");
        var capabilities = (com.fasterxml.jackson.databind.node.ArrayNode)manifest.path("capabilities");
        capabilities.add("learned-reranking-v1");
        assertThatThrownBy(() -> KnowledgeRetrievalSettings.requireRuntimeSupport(provider, manifest)).hasMessageContaining("deployment-local-reranking-budget-v1");
        capabilities.add("deployment-local-reranking-budget-v1");
        assertThatCode(() -> KnowledgeRetrievalSettings.requireRuntimeSupport(provider, manifest)).doesNotThrowAnyException();
        ((com.fasterxml.jackson.databind.node.ObjectNode)provider.path("knowledgeRetrieval")).put("enabled", false);
        assertThat(KnowledgeRetrievalSettings.profileEnabled(provider)).isFalse();
        assertThatCode(() -> KnowledgeRetrievalSettings.requireRuntimeSupport(provider, null)).doesNotThrowAnyException();
        assertThatCode(() -> KnowledgeRetrievalSettings.requireRuntimeSupport(mapper.readTree("{}"), null)).doesNotThrowAnyException();
    }
}
