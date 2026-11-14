package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedBrainQualityDefaultsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentCuratedModuleCatalogService curated =
        new DeploymentCuratedModuleCatalogService(mapper, new DefaultResourceLoader());

    @Test
    void catalogueAndModelUseTheSharedSelectivePolicy() {
        assertThat(ManagedBrainQualityDefaults.openAiModel()).isEqualTo("gpt-5.4-mini-2026-03-17");
        assertThat(ManagedBrainQualityDefaults.openAiMaxTokens()).isEqualTo(2048);
        assertThat(ManagedBrainQualityDefaults.behaviourSelections()).isEqualTo(Map.of(
            "tone", "consultative", "answerBreadth", "grounded", "answerLength", "balanced", "nextSteps", "contextual"));
        assertThat(mapper.valueToTree(DeploymentAiBehaviourProfiles.catalogue()).path("defaults"))
            .isEqualTo(mapper.valueToTree(ManagedBrainQualityDefaults.behaviourSelections()));
    }

    @Test
    void curatedBusinessInstructionsAreRetainedWhileNewBrainsGetQualityDefaults() {
        for (String module : new String[]{"default", "support", "commerce"}) {
            JsonNode base = curated.promptPreset(module);
            JsonNode snapshot = base.deepCopy();
            ObjectNode result = ManagedBrainQualityDefaults.promptConfig(base);
            assertThat(base).isEqualTo(snapshot);
            for (String key : new String[]{"systemPrompt", "actionSelectionPrompt", "clarificationPrompt",
                "answerGenerationPrompt", "retrievalPrompt", "assistantUiPrompt"}) {
                assertThat(result.path(key)).as(module + ":" + key).isEqualTo(base.path(key));
            }
            assertThat(result.path("intentExtractionPrompt").asText())
                .startsWith(base.path("intentExtractionPrompt").asText())
                .contains("authorized conversation context", "Preserve authorization", "internal knowledge domains")
                .doesNotContain("Harbor", "GBP499", "AutoTrader");
            assertThat(result.at("/aiBehaviour/tone/presetId").asText()).isEqualTo("consultative");
            assertThat(result.at("/aiBehaviour/answerBreadth/presetId").asText()).isEqualTo("grounded");
            assertThat(result.at("/aiBehaviour/nextSteps/presetId").asText()).isEqualTo("contextual");
            assertThat(result.path("ragMaxDocumentsUsedForContext").asInt()).isEqualTo(8);
            assertThat(result.path("ragMaxContextChars").asInt()).isEqualTo(8000);
            assertThat(result.path("ragSimilarityThreshold")).isEqualTo(base.path("ragSimilarityThreshold"));
            assertThat(result.has("semanticRecovery")).isFalse();
        }
    }

    @Test
    void largerContextAndSourceSpecificThresholdsAreNotReset() {
        ObjectNode base = curated.promptPreset("default").deepCopy();
        base.put("ragMaxDocumentsUsedForContext", 12).put("ragMaxContextChars", 16000).put("ragSimilarityThreshold", 0.61);
        JsonNode result = ManagedBrainQualityDefaults.promptConfig(base);
        assertThat(result.path("ragMaxDocumentsUsedForContext").asInt()).isEqualTo(12);
        assertThat(result.path("ragMaxContextChars").asInt()).isEqualTo(16000);
        assertThat(result.path("ragSimilarityThreshold").asDouble()).isEqualTo(0.61);
    }

    @Test
    void providerDefaultsAreBoundedAndDoNotGrantCapabilitiesOrRecovery() throws Exception {
        ObjectNode provider = (ObjectNode) mapper.readTree("{\"llmProvider\":\"openai\",\"vectorStrategy\":\"milvus\"}");
        ManagedBrainQualityDefaults.seedProviderConfig(provider);
        JsonNode snapshot = provider.deepCopy();
        ManagedBrainQualityDefaults.seedProviderConfig(provider);
        assertThat(provider).isEqualTo(snapshot);
        assertThat(provider.path("vectorStrategy").asText()).isEqualTo("milvus");
        assertThat(provider.path("openaiMaxTokens").asInt()).isEqualTo(2048);
        JsonNode executor = provider.at("/orchestrationModeOverrides/executor");
        assertThat(executor.path("knowledgeBaseOverviewEnabled").asBoolean()).isTrue();
        assertThat(executor.at("/readActionResolution/planningMode").asText()).isEqualTo("ITERATIVE");
        assertThat(executor.at("/readActionResolution/maxIterations").asInt()).isEqualTo(2);
        assertThat(executor.at("/readActionResolution/maxActionEvidenceItems").asInt()).isEqualTo(10);
        assertThat(executor.at("/readActionResolution/maxActionEvidenceCharsPerAction").asInt()).isEqualTo(8000);
        assertThat(executor.at("/readActionResolution/maxPlannerContextChars").asInt()).isEqualTo(12000);
        assertThat(executor.at("/readActionResolution/ragCooperationMode").asText()).isEqualTo("RAG_IF_ACTIONS_INSUFFICIENT");
        assertThat(executor.has("rag")).isFalse();
        assertThat(executor.has("semanticRecovery")).isFalse();
        assertThat(executor.at("/readActionResolution/allowedReadActions").isMissingNode()).isTrue();
    }

    @Test
    void explicitProviderAndPermissionSettingsSurviveSeeding() throws Exception {
        ObjectNode provider = (ObjectNode) mapper.readTree("""
            {"llmProvider":"openai","openaiMaxTokens":4096,"openaiModel":"gpt-4o-mini",
             "orchestrationModeOverrides":{"navigator":{"actionsEnabled":false},
              "executor":{"knowledgeBaseOverviewEnabled":false,"semanticRecovery":{"missingTarget":"DENY"},
               "readActionResolution":{"enabled":false,"maxActionEvidenceItems":20,"allowedReadActions":["own-read"]}}}}
            """);
        JsonNode before = provider.deepCopy();
        ManagedBrainQualityDefaults.seedProviderConfig(provider);
        assertThat(provider.path("openaiModel")).isEqualTo(before.path("openaiModel"));
        assertThat(provider.path("openaiMaxTokens").asInt()).isEqualTo(4096);
        assertThat(provider.at("/orchestrationModeOverrides/navigator")).isEqualTo(before.at("/orchestrationModeOverrides/navigator"));
        assertThat(provider.at("/orchestrationModeOverrides/executor/knowledgeBaseOverviewEnabled").asBoolean()).isFalse();
        assertThat(provider.at("/orchestrationModeOverrides/executor/semanticRecovery")).isEqualTo(before.at("/orchestrationModeOverrides/executor/semanticRecovery"));
        JsonNode read = provider.at("/orchestrationModeOverrides/executor/readActionResolution");
        assertThat(read.path("enabled").asBoolean()).isFalse();
        assertThat(read.path("maxActionEvidenceItems").asInt()).isEqualTo(20);
        assertThat(read.path("allowedReadActions")).isEqualTo(before.at("/orchestrationModeOverrides/executor/readActionResolution/allowedReadActions"));
    }

    @Test
    void otherProvidersAreNotReplacedByOpenAi() throws Exception {
        ObjectNode provider = (ObjectNode) mapper.readTree("{\"llmProvider\":\"anthropic\",\"anthropicModel\":\"own-model\"}");
        ManagedBrainQualityDefaults.seedProviderConfig(provider);
        assertThat(provider.path("llmProvider").asText()).isEqualTo("anthropic");
        assertThat(provider.path("anthropicModel").asText()).isEqualTo("own-model");
        assertThat(provider.has("openaiModel")).isFalse();
        assertThat(provider.has("openaiMaxTokens")).isFalse();
    }
}
