package com.ai.fabric.platform.backend.deployment.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DeploymentConversationalAutonomyConfigTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void ordinaryProductsRetainStandardDefaultsAndExistingStoredValues() throws Exception {
        var provider = mapper.readTree("{\"openaiModel\":\"existing\",\"conversationalAutonomy\":{\"enabled\":false,\"maxWritesPerSegment\":0,\"future\":true}}");
        var before = provider.deepCopy();
        assertThat(DeploymentConversationalAutonomyConfig.enabled(provider)).isFalse();
        assertThat(DeploymentConversationalAutonomyConfig.environment(provider))
            .containsExactlyEntriesOf(java.util.Map.of("AI_ORCHESTRATION_AUTONOMY_ENABLED", "false"));
        assertThat(provider).isEqualTo(before);
    }
    @Test void enabledPolicyUsesFiniteNativeSdkDefaultsAndCanonicalConversationControl() throws Exception {
        var provider = mapper.readTree("{\"conversationalAutonomy\":{\"enabled\":true,\"maxWritesPerSegment\":0,\"maxElapsedSeconds\":90,\"instructions\":\"Exact instructions.\\n\"}}");
        assertThat(DeploymentConversationalAutonomyConfig.environment(provider))
            .containsEntry("AI_ORCHESTRATION_AUTONOMY_ENABLED", "true")
            .containsEntry("AI_ORCHESTRATION_AUTONOMY_DEFAULT_ENABLED", "true")
            .containsEntry("AI_ORCHESTRATION_AUTONOMY_MAX_WRITES_PER_SEGMENT", "0")
            .containsEntry("AI_ORCHESTRATION_AUTONOMY_MAX_ELAPSED_SECONDS", "90")
            .containsEntry("AI_ORCHESTRATION_AUTONOMY_MAX_MODEL_CALLS_PER_SEGMENT", "12")
            .containsEntry("AI_ORCHESTRATION_AUTONOMY_INSTRUCTIONS", "Exact instructions.\n");
        assertThat(StandaloneConversationRuntimeArtifactGate.required(provider)).isTrue();
    }
    @Test void executionBoundsAndBooleanTypesAreActualContractsNotSemanticChecks() throws Exception {
        for (String policy : new String[]{"[]", "{\"enabled\":\"true\"}", "{\"maxElapsedSeconds\":0}",
            "{\"maxWritesPerSegment\":2,\"maxActionsPerSegment\":1}", "{\"maxModelCallsPerSegment\":1.5}",
            "{\"maxConsecutivePlannerFailures\":1000}"}) {
            var provider = mapper.createObjectNode().set(DeploymentConversationalAutonomyConfig.FIELD, mapper.readTree(policy));
            assertThatThrownBy(() -> DeploymentConversationalAutonomyConfig.validate(provider)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void anOlderRuntimeCannotClaimAutonomousSupport() throws Exception {
        assertThatThrownBy(() -> DeploymentConversationalAutonomyConfig.requireSupport(mapper.readTree("{\"capabilities\":[\"configured-workflow-assistance\"]}")))
            .hasMessageContaining("does not support autonomous conversations");
        assertThatCode(() -> DeploymentConversationalAutonomyConfig.requireSupport(mapper.readTree("{\"capabilities\":[\"conversational-autonomy-v1\"]}")))
            .doesNotThrowAnyException();
    }
    @Test void nullProviderIsDisabledAndLiveReadbackRequiresActualLimits() throws Exception {
        assertThat(DeploymentConversationalAutonomyConfig.environment(null)).containsEntry("AI_ORCHESTRATION_AUTONOMY_ENABLED", "false");
        var provider = mapper.readTree("{\"conversationalAutonomy\":{\"enabled\":true}}");
        var actual = mapper.readTree("""
            {"enabled":true,"defaultEnabled":true,"execution":"ACTIVE_REQUEST","maxElapsedSeconds":120,"segmentSeconds":30,
             "maxActionsPerSegment":8,"maxWritesPerSegment":3,"maxModelCallsPerSegment":12,"maxCorrectionsPerSegment":3,
             "maxConsecutiveNoProgress":8,"maxConsecutivePlannerFailures":4,"replanNonAuthorityReadFailures":true}
            """);
        assertThat(DeploymentConversationalAutonomyConfig.statusMatches(provider, actual)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) actual).put("maxElapsedSeconds", 300);
        assertThat(DeploymentConversationalAutonomyConfig.statusMatches(provider, actual)).isFalse();
        assertThat(DeploymentConversationalAutonomyConfig.statusMatches(provider, null)).isFalse();
    }
}
