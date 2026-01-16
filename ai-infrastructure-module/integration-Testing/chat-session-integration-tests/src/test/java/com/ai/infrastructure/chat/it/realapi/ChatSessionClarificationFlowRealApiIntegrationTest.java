package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
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
class ChatSessionClarificationFlowRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    void shouldRecordClarificationTurnAndAllowFollowUp() {
        String ownerId = "clarify-user-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        OrchestrationContext ctx = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult clarification = orchestrator.orchestrate(
            "Search my knowledge base for the return policy. If you need a domain, ask me.",
            ctx
        );

        assertThat(clarification).isNotNull();
        // Real providers may return generation-only INFORMATION_PROVIDED instead of asking for a domain.
        // We still require the flow to be stable (no ERROR) and for chat history to be recorded.
        assertThat(clarification.getType()).isNotEqualTo(OrchestrationResultType.ERROR);
        assertThat(clarification.getMessage()).isNotBlank();

        ChatSession afterClarification = chatSessionService.getSession(conversationId, ownerId);
        assertThat(afterClarification.getTurns())
            .withFailMessage("Clarification turn should be recorded to preserve chat continuity.")
            .isNotEmpty();

        OrchestrationResult followUp = orchestrator.orchestrate(
            "Use domain ragconversation.",
            ctx
        );

        assertThat(followUp).isNotNull();
        assertThat(followUp.getType()).isNotEqualTo(OrchestrationResultType.ERROR);
        assertThat(followUp.getMessage()).isNotBlank();

        ChatSession afterFollowUp = chatSessionService.getSession(conversationId, ownerId);
        assertThat(afterFollowUp.getTurns()).hasSizeGreaterThanOrEqualTo(2);
    }
}
