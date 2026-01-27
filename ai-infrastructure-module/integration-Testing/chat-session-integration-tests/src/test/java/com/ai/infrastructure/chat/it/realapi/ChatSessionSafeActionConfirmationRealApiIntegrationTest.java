package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
import com.ai.infrastructure.chat.it.actions.SafeEchoActionHandler;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("realapi")
class ChatSessionSafeActionConfirmationRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    void shouldExecuteSafeActionAndExposeConfirmationMessage() {
        String ownerId = "action-user-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        OrchestrationContext ctx = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult result = orchestrator.orchestrate(
            "Use the action '" + SafeEchoActionHandler.ACTION_NAME + "' with params: {\"message\":\"hello\"}.",
            ctx
        );

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSanitizedPayload()).isNotEmpty();

        Map<String, Object> data = result.getData();
        assertThat(data).isNotNull();
        assertThat(data.get("action")).isEqualTo(SafeEchoActionHandler.ACTION_NAME);
        assertThat(data.get("confirmationMessage")).isEqualTo("Executing safe_echo");

        Object actionResultRaw = data.get("actionResult");
        assertThat(actionResultRaw).isInstanceOf(ActionResult.class);
        ActionResult actionResult = (ActionResult) actionResultRaw;
        assertThat(actionResult.isSuccess()).isTrue();
        assertThat(actionResult.getData()).isNotNull();
        assertThat(actionResult.getData().toMap().get("echo")).isEqualTo("hello");

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).isNotEmpty();
    }
}
