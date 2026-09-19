package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeCapabilityManifestServiceTest {

    @Test
    void packagedManifestAdvertisesEveryImplementedBehaviorAndDurableExtension() {
        RuntimeCapabilityManifestService service = new RuntimeCapabilityManifestService(new ObjectMapper());
        service.load();
        JsonNode manifest = service.manifest();

        assertThat(strings(manifest.path("supportedBehaviorTypes")))
            .containsExactly("AGENTIC_SPECIALIST_TEAM", "CONVERSATIONAL", "SMART_BRAIN");
        assertThat(strings(manifest.path("supportedExecutionExtensions"))).containsExactly("HUMAN_REVIEW");
        assertThat(strings(manifest.path("capabilities"))).contains(
            "specialist-chains",
            "jdbc-specialist-chain-state",
            "smart-brain-durable-operations",
            "action-proposal-human-review",
            "jdbc-review-state"
        );
        assertThat(strings(manifest.path("migrationIds"))).contains(
            "ai-specialist-chain-execution-v1",
            "loomai-smart-brain-operation-v1",
            "ai-action-proposal-receipt-v1",
            "ai-review-task-v1",
            "ai-review-dispatch-v1"
        );
        assertThat(strings(manifest.path("verificationPackIds"))).contains(
            "agentic-specialist-team-v1",
            "smart-brain-behavior-v1",
            "human-review-action-proposal-v1"
        );
        Map<String, Object> projection = service.manifestProjection();
        assertThat(projection).containsEntry("schemaVersion", "loomai-runtime-capabilities-v1");
        assertThat(projection.get("supportedBehaviorTypes")).isEqualTo(List.of(
            "AGENTIC_SPECIALIST_TEAM",
            "CONVERSATIONAL",
            "SMART_BRAIN"
        ));
        assertThat(projection).doesNotContainKeys("array", "object", "textual");
        assertThat(service.manifestHash()).matches("[a-f0-9]{64}");
    }

    private List<String> strings(JsonNode values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .map(JsonNode::asText)
            .toList();
    }
}
