package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.exception.ChatSessionAccessDeniedException;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.action.PendingAction;
import com.ai.infrastructure.intent.action.PendingActionStore;
import com.ai.infrastructure.intent.actiondraft.ActionDraftStore;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationEnrichmentStepTest {

    @Test
    void shouldEnrichProcessedQueryWhenHistoryPresent() {
        ChatSessionService service = mock(ChatSessionService.class);
        when(service.getConversationContext(anyString(), anyString())).thenReturn("User: hi\nAssistant: hello");
        PendingActionStore pendingActionStore = mock(PendingActionStore.class);
        when(pendingActionStore.peekPendingAction(anyString(), anyString())).thenReturn(Optional.empty());
        ActionDraftStore actionDraftStore = mock(ActionDraftStore.class);
        when(actionDraftStore.peekDraft(anyString(), anyString())).thenReturn(Optional.empty());

        ChatSessionProperties properties = new ChatSessionProperties();
        properties.setEnabled(true);
        properties.setWindowSize(5);
        properties.setMaxContextChars(10_000);

        ConversationEnrichmentStep step = new ConversationEnrichmentStep(service, properties, pendingActionStore, actionDraftStore);

        OrchestrationContext orchestrationContext = OrchestrationContext.builder()
            .userId("user-1")
            .conversationId("conv-1")
            .build();

        PipelineContext context = PipelineContext.from("What next?", orchestrationContext);
        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getProcessedQuery()).contains("Conversation History");
        assertThat(updated.getProcessedQuery()).contains("User: hi");
        assertThat(updated.getProcessedQuery()).contains("Current Query");
        assertThat(updated.getProcessedQuery()).contains("What next?");
        assertThat(updated.getMetadata()).containsKey("chat");
        @SuppressWarnings("unchecked")
        Map<String, Object> chatMeta = (Map<String, Object>) updated.getMetadata().get("chat");
        assertThat(chatMeta).containsEntry("conversationId", "conv-1");
    }

    @Test
    void shouldTerminateWhenAccessDenied() {
        ChatSessionService service = mock(ChatSessionService.class);
        when(service.getConversationContext(anyString(), anyString())).thenThrow(new ChatSessionAccessDeniedException("denied"));
        PendingActionStore pendingActionStore = mock(PendingActionStore.class);
        when(pendingActionStore.peekPendingAction(anyString(), anyString())).thenReturn(Optional.empty());
        ActionDraftStore actionDraftStore = mock(ActionDraftStore.class);
        when(actionDraftStore.peekDraft(anyString(), anyString())).thenReturn(Optional.empty());

        ChatSessionProperties properties = new ChatSessionProperties();
        properties.setEnabled(true);

        ConversationEnrichmentStep step = new ConversationEnrichmentStep(service, properties, pendingActionStore, actionDraftStore);

        OrchestrationContext orchestrationContext = OrchestrationContext.builder()
            .userId("user-1")
            .conversationId("conv-1")
            .build();

        PipelineContext context = PipelineContext.from("What next?", orchestrationContext);
        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isTrue();
        assertThat(updated.getEarlyTerminationResult()).isNotNull();
        assertThat(updated.getEarlyTerminationResult().getType()).isEqualTo(OrchestrationResultType.ERROR);
        assertThat(updated.getEarlyTerminationResult().getErrorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void shouldEnrichConfirmationContextWhenPendingActionExists() {
        ChatSessionService service = mock(ChatSessionService.class);
        when(service.getConversationContext(anyString(), anyString())).thenReturn("User: hi\nAssistant: hello");

        PendingActionStore pendingActionStore = mock(PendingActionStore.class);
        when(pendingActionStore.peekPendingAction(anyString(), anyString())).thenReturn(Optional.of(
            new PendingAction("create_purchase_order", Map.of("sku", "X"), "Create purchase order for 1 × X?", Instant.now())
        ));
        ActionDraftStore actionDraftStore = mock(ActionDraftStore.class);
        when(actionDraftStore.peekDraft(anyString(), anyString())).thenReturn(Optional.empty());

        ChatSessionProperties properties = new ChatSessionProperties();
        properties.setEnabled(true);

        ConversationEnrichmentStep step = new ConversationEnrichmentStep(service, properties, pendingActionStore, actionDraftStore);

        OrchestrationContext orchestrationContext = OrchestrationContext.builder()
            .userId("user-1")
            .conversationId("conv-1")
            .build();

        PipelineContext context = PipelineContext.from("yes", orchestrationContext);
        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getProcessedQuery()).contains("CONFIRMATION CONTEXT:");
        assertThat(updated.getProcessedQuery()).contains("pendingAction (most recent):");
        assertThat(updated.getProcessedQuery()).contains("create_purchase_order");
    }
}
