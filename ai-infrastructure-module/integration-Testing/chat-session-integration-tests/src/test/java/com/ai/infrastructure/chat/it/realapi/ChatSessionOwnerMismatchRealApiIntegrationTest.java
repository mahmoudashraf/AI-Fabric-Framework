package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.exception.ChatSessionAccessDeniedException;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("realapi")
class ChatSessionOwnerMismatchRealApiIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Test
    void shouldDenyAccessWhenOwnerDoesNotMatch() {
        String ownerA = "owner-a-" + UUID.randomUUID();
        String ownerB = "owner-b-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        chatSessionService.recordTurn(conversationId, ownerA, "Hello", "Hi");

        assertThatThrownBy(() -> chatSessionService.getSession(conversationId, ownerB))
            .isInstanceOf(ChatSessionAccessDeniedException.class);
    }
}

