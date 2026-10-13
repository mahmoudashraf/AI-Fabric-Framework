package com.ai.fabric.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.fabric.chat.control.ConversationControlState;
import ai.fabric.chat.control.ConversationMessage;
import ai.fabric.chat.control.ConversationParticipantRole;
import ai.fabric.chat.control.ConversationScope;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.handover.ConversationHandoverContracts.AiInteractionAdmission;
import com.ai.fabric.runtime.handover.ConversationHandoverContracts.ConversationView;
import com.ai.fabric.runtime.handover.ConversationHandoverContracts.HumanMessageAcceptance;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.ai.fabric.runtime.handover.RuntimeConversationTurn;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.ConversationHandoverController;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ConversationHandoverControllerTest {

    private static final String CONVERSATION_ID = "chat-1";
    private static final String IDEMPOTENCY_KEY = "message-1";

    private RuntimeRequestAuthResolver authResolver;
    private RuntimeConversationHandoverService handoverService;
    private ChatRuntimeController chatRuntimeController;
    private ConversationHandoverController controller;
    private HttpServletRequest request;
    private RuntimeResolvedIdentity identity;

    @BeforeEach
    void setUp() {
        authResolver = mock(RuntimeRequestAuthResolver.class);
        handoverService = mock(RuntimeConversationHandoverService.class);
        chatRuntimeController = mock(ChatRuntimeController.class);
        controller = new ConversationHandoverController(
            authResolver,
            handoverService,
            chatRuntimeController,
            mock(TaskExecutor.class)
        );
        request = mock(HttpServletRequest.class);
        identity = RuntimeResolvedIdentity.builder()
            .authContext(RuntimeAuthContext.builder()
                .subjectId("anon-owner-1")
                .subjectType(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
                .authMode(RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS)
                .tenantId("tenant-1")
                .deploymentId("deployment-1")
                .sessionId("session-1")
                .grantedScopes(List.of(RuntimeScopeCatalog.CHAT_MESSAGES))
                .build())
            .warnings(List.of())
            .build();
        when(authResolver.resolveVerifiedForChat(request)).thenReturn(identity);
    }

    @Test
    void aiActiveMessageUsesCanonicalPersistenceLeaseAndCompletionPath() {
        ChatQueryRequest body = query("Which option is best?");
        ConversationView active = view(ConversationControlState.AI_ACTIVE);
        ConversationScope scope = new ConversationScope(
            "tenant-1", "deployment-1", CONVERSATION_ID, "anon-owner-1"
        );
        ConversationMessage userMessage = new ConversationMessage(
            "user-message-1",
            scope,
            1,
            ConversationParticipantRole.USER,
            "anon-owner-1",
            body.getQuery(),
            IDEMPOTENCY_KEY,
            Map.of(),
            Instant.parse("2026-10-13T00:00:00Z")
        );
        RuntimeConversationTurn turn = mock(RuntimeConversationTurn.class);
        ChatQueryResponse payload = ChatQueryResponse.builder()
            .success(true)
            .type("INFORMATION_PROVIDED")
            .answer("The first option is the best fit.")
            .conversationId(CONVERSATION_ID)
            .build();

        when(handoverService.view(identity, CONVERSATION_ID, 0)).thenReturn(active);
        when(handoverService.appendUserMessage(
            eq(identity), eq(CONVERSATION_ID), eq(IDEMPOTENCY_KEY), eq(body.getQuery()), any()
        )).thenReturn(userMessage);
        when(handoverService.claimAiInteraction(
            identity, CONVERSATION_ID, IDEMPOTENCY_KEY, userMessage.messageId(), body
        )).thenReturn(new AiInteractionAdmission("CLAIMED", null, null, null));
        when(handoverService.beginAiTurn(
            identity, CONVERSATION_ID, userMessage.sequence(), IDEMPOTENCY_KEY
        )).thenReturn(turn);
        when(chatRuntimeController.controlledQuery(body, request, turn))
            .thenReturn(ResponseEntity.ok(payload));

        ResponseEntity<ChatQueryResponse> response = controller.message(
            CONVERSATION_ID, IDEMPOTENCY_KEY, body, request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(payload);
        assertThat(body.getConversationId()).isEqualTo(CONVERSATION_ID);
        verify(authResolver).requireScope(
            identity,
            RuntimeScopeCatalog.CHAT_MESSAGES,
            "/api/chat/me/conversations/{conversationId}/messages"
        );
        verify(handoverService).appendAiMessage(
            turn,
            IDEMPOTENCY_KEY + ":ai",
            payload.getAnswer(),
            Map.of("providerRequestId", "", "resultType", payload.getType())
        );
        verify(handoverService).completeAiInteraction(
            identity, CONVERSATION_ID, IDEMPOTENCY_KEY, HttpStatus.OK.value(), payload
        );
    }

    @Test
    void humanActiveMessageBypassesAiAndUsesHumanChannelPath() {
        ChatQueryRequest body = query("I can speak now.");
        ConversationView active = view(ConversationControlState.HUMAN_ACTIVE);
        when(handoverService.view(identity, CONVERSATION_ID, 0)).thenReturn(active);
        when(handoverService.appendHumanBoundUserMessage(
            identity,
            CONVERSATION_ID,
            IDEMPOTENCY_KEY,
            body.getQuery(),
            List.of()
        )).thenReturn(new HumanMessageAcceptance("ACCEPTED", "human-message-1", active));

        ResponseEntity<ChatQueryResponse> response = controller.message(
            CONVERSATION_ID, IDEMPOTENCY_KEY, body, request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).isEqualTo("HUMAN_MESSAGE_ACCEPTED");
        assertThat(response.getBody().getMetadata()).containsEntry("conversationControl", active);
        verify(chatRuntimeController, never()).controlledQuery(any(), any(), any());
        verify(handoverService, never()).claimAiInteraction(any(), any(), any(), any(), any());
        verify(handoverService, never()).appendAiMessage(any(), any(), any(), any());
    }

    private ChatQueryRequest query(String text) {
        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery(text);
        return request;
    }

    private ConversationView view(ConversationControlState state) {
        return new ConversationView(
            CONVERSATION_ID,
            state,
            1,
            state == ConversationControlState.AI_ACTIVE ? null : "handover-1",
            state == ConversationControlState.AI_ACTIVE ? null : "basic-inbox-1",
            state.name(),
            state == ConversationControlState.HUMAN_ACTIVE ? "Alice" : null,
            List.of(),
            List.of(),
            Instant.parse("2026-10-13T00:00:00Z")
        );
    }
}
