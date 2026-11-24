package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.gateway.*;
import ai.fabric.execution.specialist.SpecialistId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RuntimeConfiguredWorkflowResultTest {
    final ObjectMapper mapper = new ObjectMapper();

    AIExecutionResult<JsonNode> failure(String reason, Map<String, Object> diagnostics) {
        return new AIExecutionResult<>("work-test", SpecialistId.parse("workflow-api@1"), AIExecutionStatus.INVALID,
            null, List.of(), diagnostics, new AIExecutionFailure(reason, "private-secret-canary", false), Instant.now(), Instant.now());
    }
    @Test void preWorkflowFailureRetainsSafeReasonAndStatusWithoutExceptionText() {
        var result = RuntimeConfiguredWorkflowDispatcher.workflowResult(failure("INPUT_OR_OUTPUT_VALIDATION_FAILED", Map.of()), mapper);
        assertThat(result.path("state").asText()).isEqualTo("STOPPED");
        assertThat(result.path("stopCode").asText()).isEqualTo("INPUT_OR_OUTPUT_VALIDATION_FAILED");
        assertThat(result.path("executionStatus").asText()).isEqualTo("INVALID");
        assertThat(result.toString()).doesNotContain("private-secret-canary");
        assertThat(result.has("usage")).isFalse();
    }
    @Test void anExistingWorkflowFailureAndItsRealReceiptsArePreserved() {
        var result = RuntimeConfiguredWorkflowDispatcher.workflowResult(failure("EXECUTION_FAILED", Map.of("workflow",
            Map.of("state", "STOPPED", "stopCode", "WORKFLOW_MODEL_LIMIT", "usage", Map.of("modelCalls", 16),
                "steps", List.of(Map.of("action", "authoring_update_draft", "effect", "EXECUTED"))))), mapper);
        assertThat(result.path("stopCode").asText()).isEqualTo("WORKFLOW_MODEL_LIMIT");
        assertThat(result.at("/usage/modelCalls").asInt()).isEqualTo(16);
        assertThat(result.at("/steps/0/effect").asText()).isEqualTo("EXECUTED");
    }
    @Test void unstructuredFailureReasonsAreNotExposedAsDiagnostics() {
        var result = RuntimeConfiguredWorkflowDispatcher.workflowResult(failure("private-secret-canary", Map.of()), mapper);
        assertThat(result.path("stopCode").asText()).isEqualTo("NATIVE_EXECUTION_FAILED");
        assertThat(result.toString()).doesNotContain("private-secret-canary");
    }
    @Test void typedClarificationUsesTheModelsOwnQuestionsWhenItsMessageIsAbsent() {
        var workflow = mapper.createObjectNode().put("state", "NEEDS_INPUT");
        workflow.putArray("questions").add("What is the API base URL?");
        assertThat(RuntimeConfiguredWorkflowDispatcher.workflowMessage(failure("CLARIFICATION_REQUIRED", Map.of()), workflow))
            .isEqualTo("What is the API base URL?");
        workflow.put("message", "Please provide the provider address.");
        assertThat(RuntimeConfiguredWorkflowDispatcher.workflowMessage(failure("CLARIFICATION_REQUIRED", Map.of()), workflow))
            .isEqualTo("Please provide the provider address.");
        workflow.remove("message"); workflow.put("state", "STOPPED");
        assertThat(RuntimeConfiguredWorkflowDispatcher.workflowMessage(failure("EXECUTION_FAILED", Map.of()), workflow)).isNull();
    }
}
