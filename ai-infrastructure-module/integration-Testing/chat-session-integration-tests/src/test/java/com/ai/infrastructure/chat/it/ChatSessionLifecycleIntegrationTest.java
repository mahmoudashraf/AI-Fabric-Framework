package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.AIChatRole;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.extraction.IntentExtractionInput;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.Pipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class ChatSessionLifecycleIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private Pipeline pipeline;

    @Autowired
    private ChatSessionService chatSessionService;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
    }

    @Test
    void shouldAutoCreateSessionAndRecordMultipleTurns() {
        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder()
                .type(IntentType.OUT_OF_SCOPE)
                .intent("out_of_scope")
                .confidence(0.9)
                .build()))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        when(intentQueryExtractor.extract(
            any(IntentExtractionInput.class),
            any(com.ai.infrastructure.intent.orchestration.OrchestrationContext.class)
        )).thenReturn(response);

        String ownerId = "chat-lifecycle-user";
        String conversationId = "conv-" + UUID.randomUUID();
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        pipeline.execute("Turn 1", orch);
        assertThat(chatSessionRepository.findById(conversationId)).isPresent();

        pipeline.execute("Turn 2", orch);
        pipeline.execute("Turn 3", orch);

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).hasSize(3);

        var messages = chatSessionService.getConversationMessages(conversationId, ownerId);
        assertThat(messages.stream().anyMatch(m -> AIChatRole.USER.equals(m.getRole()) && "Turn 1".equals(m.getContent()))).isTrue();
        assertThat(messages.stream().anyMatch(m -> AIChatRole.USER.equals(m.getRole()) && "Turn 2".equals(m.getContent()))).isTrue();
        assertThat(messages.stream().anyMatch(m -> AIChatRole.USER.equals(m.getRole()) && "Turn 3".equals(m.getContent()))).isTrue();
        assertThat(messages.stream().anyMatch(m -> AIChatRole.ASSISTANT.equals(m.getRole()))).isTrue();
    }
}
