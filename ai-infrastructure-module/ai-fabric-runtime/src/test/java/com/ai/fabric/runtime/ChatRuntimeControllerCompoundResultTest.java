package com.ai.fabric.runtime;

import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class ChatRuntimeControllerCompoundResultTest {
    private final ChatRuntimeController controller = mock(ChatRuntimeController.class, CALLS_REAL_METHODS);

    @Test
    void preservesSanitizedAnswersAndFailedChildStatusWithoutChangingParentOutcome() throws Exception {
        var result = compound(Map.of("children", List.of(
            Map.of("type", "INFORMATION_PROVIDED", "success", true,
                "message", "**Approved policy:** bring photo identification.",
                "data", Map.of("internalId", "private-child-target")),
            Map.of("type", "CLARIFICATION_REQUIRED", "success", false,
                "message", "Which item do you mean?", "errorCode", "TARGET_REQUIRED",
                "data", Map.of("secret", "private-child-secret"))
        )));

        var response = project(result);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getType()).isEqualTo("COMPOUND_HANDLED");
        assertThat(response.getAnswer()).isEqualTo("Some items need clarification.");
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().getFirst().answer())
            .isEqualTo("**Approved policy:** bring photo identification.");
        assertThat(response.getResults().getFirst().success()).isTrue();
        assertThat(response.getResults().get(1).success()).isFalse();
        assertThat(response.getResults().get(1).fallbackReason()).isEqualTo("TARGET_REQUIRED");
        assertThat(new ObjectMapper().writeValueAsString(response))
            .doesNotContain("private-child-target", "private-child-secret", "internalId");
    }

    @Test
    void neverFallsBackToRawChildrenOrRawData() {
        var raw = OrchestrationResult.builder().type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true).message("UNSANITIZED ANSWER").build();
        var result = compound(Map.of());
        result.setChildren(List.of(raw));
        result.setData(Map.of("results", List.of(raw)));

        assertThat(project(result).getResults()).isEmpty();
    }

    @Test
    void supportsSanitizerOwnedDataResultsWhenNoChildrenFieldExists() {
        var result = compound(Map.of("data", Map.of("results", List.of(
            Map.of("type", "INFORMATION_PROVIDED", "success", true,
                "message", "Retrieved evidence.", "data", Map.of("answer", "The approved fee is GBP10."))
        ))));

        assertThat(project(result).getResults()).singleElement()
            .satisfies(item -> assertThat(item.answer()).isEqualTo("The approved fee is GBP10."));
    }

    @Test
    void explicitEmptySanitizedChildrenPreventAlternateDataResultsFromReappearing() {
        var result = compound(Map.of("children", List.of(), "data", Map.of("results", List.of(
            Map.of("type", "INFORMATION_PROVIDED", "success", true, "message", "Not selected.")
        ))));

        assertThat(project(result).getResults()).isEmpty();
    }

    @Test
    void malformedNestedPayloadsCannotAcquireSuccessfulStatuses() {
        var result = compound(Map.of("children", Arrays.asList(null, "not-a-result",
            Map.of("type", "INFORMATION_PROVIDED", "success", "true", "message", "Invalid status."),
            Map.of("success", true, "message", "Missing type."),
            Map.of("type", "ERROR", "success", false, "message", "Could not finish this part.")
        )));

        assertThat(project(result).getResults()).singleElement().satisfies(item -> {
            assertThat(item.success()).isFalse();
            assertThat(item.fallbackReason()).isEqualTo("ERROR");
        });
    }

    @Test
    void synthesizedInformationDoesNotDuplicateItsChildren() throws Exception {
        var result = compound(Map.of("children", List.of(
            Map.of("type", "INFORMATION_PROVIDED", "success", true, "message", "Child answer.")
        )));
        result.setType(OrchestrationResultType.INFORMATION_PROVIDED);

        var response = project(result);

        assertThat(response.getResults()).isEmpty();
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("\"results\"");
    }

    private OrchestrationResult compound(Map<String, Object> sanitized) {
        return OrchestrationResult.builder().type(OrchestrationResultType.COMPOUND_HANDLED)
            .success(true).message("Some items need clarification.")
            .sanitizedPayload(sanitized).build();
    }

    private ChatQueryResponse project(OrchestrationResult result) {
        return ReflectionTestUtils.invokeMethod(controller, "toCanonicalChatResponse",
            new ChatQueryRequest(), "chat-1", OrchestrationContext.anonymous(), result, null);
    }
}
