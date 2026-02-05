package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.ActionResultContracts;
import com.ai.infrastructure.intent.action.ActionTargetRef;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
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

        verify(chatSessionService).recordTurn(
            "conv-1",
            "user-1",
            "My ssn is [REDACTED]",
            "safe message",
            Map.of("_resultType", "INFORMATION_PROVIDED")
        );
    }

    @Test
    void shouldPersistResolvedTargetsIntoSessionMetadata() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);

        ChatSessionProperties properties = new ChatSessionProperties();
        properties.setEnabled(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        when(chatSessionService.getSession(anyString(), anyString())).thenReturn(ChatSession.builder()
            .id("conv-1")
            .ownerId("user-1")
            .turns(java.util.List.of(
                com.ai.infrastructure.chat.domain.ChatTurn.builder().build()
            ))
            .createdAt(java.time.LocalDateTime.now())
            .lastInteractionAt(java.time.LocalDateTime.now())
            .build());

        ConversationRecordingStep step = new ConversationRecordingStep(chatSessionService, properties, provider);

        OrchestrationContext orchestrationContext = OrchestrationContext.builder()
            .userId("user-1")
            .conversationId("conv-1")
            .attachmentsNormalized(java.util.List.of(NormalizedAttachment.builder()
                .contentText("snippet")
                .build()))
            .build();

        OrchestrationResult result = OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message("ok")
            .build();

        PipelineContext context = PipelineContext.from("summarize this", orchestrationContext)
            .toBuilder()
            .intentResult(result)
            .resolvedTargets(java.util.List.of(ResolvedTarget.builder()
                .id(null)
                .vectorSpace("product")
                .contentText("snippet")
                .metadata(Map.of("sku", "SKU-1"))
                .source(ResolvedTargetSource.REQUEST_ATTACHMENTS)
                .build()))
            .sanitizedPayload(Map.of("message", "ok"))
            .build();

        step.process(context);

        verify(chatSessionService).mergeSessionMetadata(eq("conv-1"), eq("user-1"), argThat(map -> {
            if (!(map.containsKey("lastResolvedTargets") && map.containsKey("lastResolvedTargetsTurnIndex"))) {
                return false;
            }
            Object rawTargets = map.get("lastResolvedTargets");
            if (!(rawTargets instanceof java.util.List<?> list) || list.isEmpty()) {
                return false;
            }
            Object first = list.getFirst();
            if (!(first instanceof Map<?, ?> entry)) {
                return false;
            }
            return !entry.containsKey("id")
                && "snippet".equals(entry.get("contentText"))
                && "REQUEST_ATTACHMENTS".equals(entry.get("originSource"));
        }));
    }

    @Test
    void shouldPersistActionListItemsAsPinnedTargets() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);

        ChatSessionProperties properties = new ChatSessionProperties();
        properties.setEnabled(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        when(chatSessionService.getSession(anyString(), anyString())).thenReturn(ChatSession.builder()
            .id("conv-1")
            .ownerId("user-1")
            .turns(java.util.List.of(
                com.ai.infrastructure.chat.domain.ChatTurn.builder().build()
            ))
            .createdAt(java.time.LocalDateTime.now())
            .lastInteractionAt(java.time.LocalDateTime.now())
            .build());

        ConversationRecordingStep step = new ConversationRecordingStep(chatSessionService, properties, provider);

        OrchestrationContext orchestrationContext = OrchestrationContext.builder()
            .userId("user-1")
            .conversationId("conv-1")
            .build();

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("ok")
            .data(ActionResultContracts.targets(java.util.List.of(
                new ActionTargetRef("85", "product", "snippet", Map.of("sku", "SKU-85"))
            )))
            .build();

        OrchestrationResult result = OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_EXECUTED)
            .success(true)
            .message("ok")
            .data(Map.of(
                "action", "list_products",
                "actionResult", actionResult
            ))
            .build();

        PipelineContext context = PipelineContext.from("list products", orchestrationContext)
            .toBuilder()
            .intentResult(result)
            .sanitizedPayload(Map.of("message", "ok"))
            .build();

        step.process(context);

        verify(chatSessionService).mergeSessionMetadata(eq("conv-1"), eq("user-1"), argThat(map -> {
            Object raw = map.get("lastResolvedTargets");
            if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) {
                return false;
            }
            Object first = list.getFirst();
            if (!(first instanceof Map<?, ?> entry)) {
                return false;
            }
            return "85".equals(entry.get("id")) && "ACTION_RESULT_ITEMS".equals(entry.get("originSource"));
        }));
    }
}
