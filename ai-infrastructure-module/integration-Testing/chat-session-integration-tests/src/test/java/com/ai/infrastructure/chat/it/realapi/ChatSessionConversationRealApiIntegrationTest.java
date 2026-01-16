package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class ChatSessionConversationRealApiIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionConversationRealApiIntegrationTest.class);

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    void shouldRecordTurnsAcrossRequests() {
        String ownerId = "realapi-chat-user";
        String conversationId = "chat-" + UUID.randomUUID();

        OrchestrationContext ctx = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        orchestrator.orchestrate("Hello. Reply with a short greeting.", ctx);
        orchestrator.orchestrate("Thanks.", ctx);

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).hasSizeGreaterThanOrEqualTo(2);

        String history = chatSessionService.getConversationContext(conversationId, ownerId);
        assertThat(history).isNotBlank();
        assertThat(history).contains("User:");
        assertThat(history).contains("Assistant:");
    }

    @Test
    void shouldRespondToConversationalQueriesAndRecordTurns() {
        String ownerId = "realapi-chat-smalltalk-user";
        String conversationId = "chat-" + UUID.randomUUID();

        OrchestrationContext ctx = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        String[] queries = {
            "How are you today?",
            "How are you?",
            "What are you?",
            "What is the weather today?"
        };

        for (String query : queries) {
            OrchestrationResult result = orchestrator.orchestrate(query, ctx);

            log.info("Conversational query result: query='{}' type={} success={} errorCode={} message={}",
                query,
                result != null ? result.getType() : null,
                result != null && result.isSuccess(),
                result != null ? result.getErrorCode() : null,
                result != null ? result.getMessage() : null
            );

            assertThat(result).isNotNull();
            assertThat(result.getType()).isNotEqualTo(com.ai.infrastructure.intent.orchestration.OrchestrationResultType.ERROR);
            assertThat(result.getMessage()).isNotBlank();
        }

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).hasSizeGreaterThanOrEqualTo(queries.length);
    }
}
