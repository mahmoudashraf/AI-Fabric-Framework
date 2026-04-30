package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.security.ResponseSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseSanitizationStepTest {

    @Test
    void shouldApplySanitizedMessageAndAnswerToResultMirrorsWithoutDroppingData() {
        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> piiProvider = mock(ObjectProvider.class);
        when(piiProvider.getIfAvailable()).thenReturn(null);

        ResponseSanitizationStep step = new ResponseSanitizationStep(
            new ResponseSanitizer(piiProvider, new ResponseSanitizationProperties())
        );
        OrchestrationResult result = OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message("Live store data has no review records. Let me know if you need anything else.")
            .data(Map.of(
                "answer", "Live store data has no review records. Let me know if you need anything else.",
                "documents", List.of(Map.of("id", "doc-1"))
            ))
            .build();
        PipelineContext context = PipelineContext
            .from("reviews?", OrchestrationContext.forUser("user-1"))
            .toBuilder()
            .intentResult(result)
            .build();

        PipelineContext updated = step.process(context);

        assertThat(result.getMessage()).isEqualTo("Live store data has no review records.");
        assertThat(result.getData())
            .containsEntry("answer", "Live store data has no review records.")
            .containsKey("documents");
        assertThat(updated.getSanitizedPayload()).containsEntry("message", "Live store data has no review records.");
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedData = (Map<String, Object>) updated.getSanitizedPayload().get("data");
        assertThat(sanitizedData).containsEntry("answer", "Live store data has no review records.");
    }
}
