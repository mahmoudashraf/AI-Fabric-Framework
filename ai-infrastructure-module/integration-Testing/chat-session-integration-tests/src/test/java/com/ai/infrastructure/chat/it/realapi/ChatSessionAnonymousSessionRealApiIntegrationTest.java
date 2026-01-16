package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
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
class ChatSessionAnonymousSessionRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    void shouldRecordTurnsForAnonymousSessionIdOwner() {
        String sessionId = "anon-session-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        OrchestrationContext ctx = OrchestrationContext.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult result = orchestrator.orchestrate("Hello. Reply with a single short sentence.", ctx);
        assertThat(result).isNotNull();
        assertThat(result.getType()).isNotEqualTo(OrchestrationResultType.ERROR);
        assertThat(result.getMessage()).isNotBlank();

        ChatSession session = chatSessionService.getSession(conversationId, sessionId);
        assertThat(session.getOwnerId()).isEqualTo(sessionId);
        assertThat(session.getTurns()).isNotEmpty();
    }
}

