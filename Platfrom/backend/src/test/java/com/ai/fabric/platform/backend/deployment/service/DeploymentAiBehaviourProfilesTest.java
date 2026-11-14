package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentAiBehaviourProfilesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentCuratedModuleCatalogService curated = new DeploymentCuratedModuleCatalogService(mapper, new DefaultResourceLoader());

    @Test
    void contextualSuggestionCostIsVisibleAndDefaultMatchesManagedQualityPolicy() {
        JsonNode catalogue = mapper.valueToTree(DeploymentAiBehaviourProfiles.catalogue());
        assertThat(catalogue.path("defaults").path("nextSteps").asText()).isEqualTo("contextual");
        assertThat(catalogue.path("defaults").path("tone").asText()).isEqualTo("consultative");
        JsonNode contextual = null;
        for (JsonNode group : catalogue.path("groups")) {
            if (!"nextSteps".equals(group.path("key").asText())) continue;
            for (JsonNode option : group.path("options")) {
                if ("contextual".equals(option.path("id").asText())) contextual = option;
            }
        }
        assertThat(contextual).isNotNull();
        assertThat(contextual.path("description").asText()).contains("completed answer", "one additional AI call");
    }

    @Test
    void absentProfilesPreserveAllExistingConfiguration() throws Exception {
        JsonNode input = mapper.readTree("{\"systemPrompt\":\"Own brand\",\"answerGenerationPrompt\":\"Own guidance\",\"ragMaxContextChars\":7000,\"customFlag\":true}");
        assertThat(DeploymentAiBehaviourProfiles.compile(input, curated.promptPreset("default"))).isEqualTo(input);
    }

    @Test
    void selectionsPersistRevisionAndInstructionsWithoutChangingBasePrompts() {
        ObjectNode input = curated.promptPreset("default").deepCopy();
        input.put("ragMaxDocumentsUsedForContext", 9);
        input.putObject("customConfig").put("keep", true);
        var original = input.deepCopy();
        ObjectNode selected = DeploymentAiBehaviourProfiles.select(input, mapper.valueToTree(Map.of("tone", "warm", "answerBreadth", "advisory")));
        assertThat(input).isEqualTo(original);
        assertThat(selected.path("systemPrompt")).isEqualTo(input.path("systemPrompt"));
        assertThat(selected.path("answerGenerationPrompt")).isEqualTo(input.path("answerGenerationPrompt"));
        assertThat(selected.path("ragMaxDocumentsUsedForContext").asInt()).isEqualTo(9);
        assertThat(selected.at("/customConfig/keep").asBoolean()).isTrue();
        assertThat(selected.at("/aiBehaviour/tone/presetId").asText()).isEqualTo("warm");
        assertThat(selected.at("/aiBehaviour/tone/revision").asInt()).isEqualTo(1);
        assertThat(selected.at("/aiBehaviour/tone/instructions").asText()).isNotBlank();
        assertThat(mapper.valueToTree(DeploymentAiBehaviourProfiles.catalogue()).path("groups")).hasSize(4);
    }

    @Test
    void everyToneAndBreadthCombinationPreservesOtherPromptsAndProviderParameters() {
        JsonNode baseline = curated.promptPreset("default");
        for (String tone : new String[]{"professional", "warm", "consultative", "technical"}) {
            for (String breadth : new String[]{"grounded", "advisory", "exploratory"}) {
                ObjectNode selected = DeploymentAiBehaviourProfiles.select(baseline,
                    mapper.valueToTree(Map.of("tone", tone, "answerBreadth", breadth, "nextSteps", "contextual")));
                var compiled = DeploymentAiBehaviourProfiles.compile(selected, baseline);
                assertThat(compiled.at("/aiBehaviour/answerBreadth/presetId").asText()).isEqualTo(breadth);
                assertThat(compiled.path("answerGenerationPrompt").asText()).contains(selected.at("/aiBehaviour/tone/instructions").asText());
                assertThat(compiled.path("answerGenerationPrompt").asText()).contains(selected.at("/aiBehaviour/answerBreadth/instructions").asText());
                assertThat(compiled.path("actionSelectionPrompt")).isEqualTo(baseline.path("actionSelectionPrompt"));
                assertThat(compiled.path("clarificationPrompt")).isEqualTo(baseline.path("clarificationPrompt"));
                assertThat(compiled.has("generationTemperature")).isFalse();
                assertThat(compiled.has("llmProvider")).isFalse();
            }
        }
    }

    @Test
    void knownCuratedGenerationInstructionsAreComposedWithoutContradictoryGroundingOrLength() {
        for (String module : new String[]{"default", "commerce", "support"}) {
            JsonNode baseline = curated.promptPreset(module);
            var selected = DeploymentAiBehaviourProfiles.select(baseline,
                mapper.valueToTree(Map.of("answerBreadth", "exploratory", "answerLength", "detailed")));
            var compiled = DeploymentAiBehaviourProfiles.compile(selected, baseline);
            assertThat(compiled.path("answerGenerationPrompt").asText())
                .doesNotContain(baseline.path("answerGenerationPrompt").asText())
                .contains("clearly identified assumptions", "Business-specific facts", "No profile provides web access");
            assertThat(compiled.path("retrievalPrompt").asText()).contains("broader alternatives");
            assertThat(compiled.path("systemPrompt")).isEqualTo(baseline.path("systemPrompt"));
        }
    }

    @Test
    void customDomainInstructionsRemainVerbatimAndVisibleInCompositionDiff() {
        ObjectNode input = curated.promptPreset("default").deepCopy();
        String custom = "Always explain the approved escalation owner and preserve ticket references.";
        input.put("answerGenerationPrompt", custom);
        var response = DeploymentAiBehaviourProfiles.compose(input,
            mapper.valueToTree(Map.of("tone", "consultative")), curated.promptPreset("default"));
        assertThat(((JsonNode) response.get("effectivePromptConfig")).path("answerGenerationPrompt").asText()).contains(custom);
        assertThat(response.get("customInstructionsPreserved")).isEqualTo(true);
        assertThat((java.util.List<?>) response.get("changes")).isNotEmpty();
        assertThat(((JsonNode) response.get("promptConfig")).path("answerGenerationPrompt").asText()).isEqualTo(custom);
    }

    @Test
    void directInstructionEditsBecomeCustomByExactIdentityAndResetIsExplicit() {
        var selected = DeploymentAiBehaviourProfiles.select(curated.promptPreset("support"), mapper.valueToTree(Map.of()));
        ((ObjectNode) selected.at("/aiBehaviour/tone")).put("instructions", "Warm and concise for our own brand.");
        var normalized = DeploymentAiBehaviourProfiles.normalize(selected);
        assertThat(normalized.at("/aiBehaviour/tone/presetId").asText()).isEqualTo("custom");
        assertThat(normalized.at("/aiBehaviour/answerBreadth/presetId").asText()).isEqualTo("grounded");
        var reset = DeploymentAiBehaviourProfiles.select(normalized, mapper.valueToTree(Map.of("tone", "warm")));
        assertThat(reset.at("/aiBehaviour/tone/presetId").asText()).isEqualTo("warm");
        assertThat(reset.at("/aiBehaviour/answerBreadth")).isEqualTo(normalized.at("/aiBehaviour/answerBreadth"));
    }

    @Test
    void preferencesDoNotChangeSamplingOrTokenLimitsAndOffDisablesOptionalEnrichment() {
        var selected = DeploymentAiBehaviourProfiles.select(curated.promptPreset("default"), mapper.valueToTree(Map.of(
            "answerLength", "detailed", "nextSteps", "off")));
        selected.put("responseGenerationMaxTokensDeep", 2800);
        var compiled = DeploymentAiBehaviourProfiles.compile(selected, curated.promptPreset("default"));
        assertThat(compiled.path("responseGenerationMaxTokensDeep").asInt()).isEqualTo(2800);
        assertThat(compiled.path("smartSuggestionsEnabled").asBoolean(true)).isFalse();
        assertThat(compiled.path("answerGenerationPrompt").asText()).contains("detailed, well-structured", "Do not add optional next-step");
        assertThat(compiled.path("intentExtractionPrompt").asText()).contains("NEXT-STEP RECOMMENDATION POLICY");
    }

    @Test
    void configurationRoundTripsAndCompilationIsDeterministic() throws Exception {
        var selected = DeploymentAiBehaviourProfiles.select(curated.promptPreset("default"), mapper.valueToTree(Map.of("answerBreadth", "advisory")));
        var reopened = mapper.readTree(mapper.writeValueAsString(selected));
        assertThat(DeploymentAiBehaviourProfiles.compile(selected, curated.promptPreset("default")))
            .isEqualTo(DeploymentAiBehaviourProfiles.compile(reopened, curated.promptPreset("default")));
        var published = DeploymentAiBehaviourProfiles.compile(selected, curated.promptPreset("default"));
        assertThat(DeploymentAiBehaviourProfiles.compile(published, curated.promptPreset("default"))).isEqualTo(published);
    }

    @Test
    void invalidOperatorProfileCodesFailWithoutGuessing() {
        assertThatThrownBy(() -> DeploymentAiBehaviourProfiles.select(mapper.createObjectNode(),
            mapper.valueToTree(Map.of("tone", "made-up")))).hasMessageContaining("Unknown AI behaviour");
        var selected = DeploymentAiBehaviourProfiles.select(mapper.createObjectNode(), mapper.createObjectNode());
        ((ObjectNode) selected.path("aiBehaviour")).put("revision", 999);
        assertThatThrownBy(() -> DeploymentAiBehaviourProfiles.normalize(selected)).hasMessageContaining("Unsupported AI behaviour");
    }
}
