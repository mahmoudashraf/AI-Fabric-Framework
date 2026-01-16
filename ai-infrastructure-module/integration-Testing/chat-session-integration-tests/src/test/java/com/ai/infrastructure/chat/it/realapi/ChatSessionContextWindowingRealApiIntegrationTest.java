package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "ai.chat.window-size=2",
        "ai.chat.max-context-chars=8000"
    }
)
@ActiveProfiles("realapi")
class ChatSessionContextWindowingRealApiIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    void shouldReturnOnlyLastNConversationTurnsInContext() {
        String ownerId = "window-user-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        chatSessionService.recordTurn(conversationId, ownerId, "Q1", "A1");
        chatSessionService.recordTurn(conversationId, ownerId, "Q2", "A2");
        chatSessionService.recordTurn(conversationId, ownerId, "Q3", "A3");

        String context = chatSessionService.getConversationContext(conversationId, ownerId);

        assertThat(context).contains("User: Q2");
        assertThat(context).contains("User: Q3");
        assertThat(context).doesNotContain("User: Q1");
    }
}

