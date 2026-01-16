package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationRecordingStepTest {

    @Test
    void shouldRecordTurnUsingSanitizedMessageAndRedactedQuery() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);

        ChatSessionProperties properties = new ChatSessionProperties();
        properties.setEnabled(true);

        PIIDetectionService piiDetectionService = mock(PIIDetectionService.class);
        when(piiDetectionService.analyze(anyString())).thenReturn(PIIDetectionResult.builder()
            .piiDetected(true)
            .processedQuery("My ssn is [REDACTED]")
            .detections(java.util.List.of())
            .build());

        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(piiDetectionService);

        ConversationRecordingStep step = new ConversationRecordingStep(chatSessionService, properties, provider);

        OrchestrationContext orchestrationContext = OrchestrationContext.builder()
            .userId("user-1")
            .conversationId("conv-1")
            .build();

        OrchestrationResult result = OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message("raw message")
            .build();

        PipelineContext context = PipelineContext.from("My ssn is 123-45-6789", orchestrationContext)
            .toBuilder()
            .intentResult(result)
            .sanitizedPayload(Map.of("message", "safe message"))
            .build();

        step.process(context);

        verify(chatSessionService).recordTurn("conv-1", "user-1", "My ssn is [REDACTED]", "safe message");
    }
}

