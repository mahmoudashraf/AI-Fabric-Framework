package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
import com.ai.infrastructure.chat.it.actions.SafeEchoActionHandler;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("realapi")
class ChatSessionActionPlusInfoCompoundRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    void shouldHandleActionThenExplanationWithoutError() {
        String ownerId = "compound-action-user-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        OrchestrationContext ctx = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult result = orchestrator.orchestrate(
            "First execute action '" + SafeEchoActionHandler.ACTION_NAME + "' with params: {\"message\":\"hello\"}. "
                + "Then briefly explain what you executed.",
            ctx
        );

        assertThat(result).isNotNull();
        assertThat(result.getType()).isNotEqualTo(OrchestrationResultType.ERROR);
        assertThat(result.getMessage()).isNotBlank();

        if (result.getChildren() != null && !result.getChildren().isEmpty()) {
            assertThat(result.getChildren()).anyMatch(child -> child != null && child.getType() == OrchestrationResultType.ACTION_EXECUTED);
        } else {
            assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        }

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).isNotEmpty();
    }
}

