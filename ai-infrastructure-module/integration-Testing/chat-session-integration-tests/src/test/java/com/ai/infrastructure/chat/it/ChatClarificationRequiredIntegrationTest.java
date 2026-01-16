package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class ChatClarificationRequiredIntegrationTest {

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
    void shouldReturnClarificationRequiredAndRecordTurn() {
        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("search")
                .confidence(0.9)
                .requiresRetrieval(true)
                .requiresGeneration(false)
                .vectorSpace(null)
                .build()))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        when(intentQueryExtractor.extract(
            anyString(),
            any(com.ai.infrastructure.intent.orchestration.OrchestrationContext.class)
        )).thenReturn(response);

        String ownerId = "chat-clarify-user";
        String conversationId = "conv-" + UUID.randomUUID();
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        String query = "Find the policy details.";
        OrchestrationResult result = pipeline.execute(query, orch);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getSanitizedPayload()).isNotEmpty();

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).hasSize(1);
        assertThat(session.getTurns().getFirst().getUserQuery()).isEqualTo(query);
        assertThat(session.getTurns().getFirst().getAiResponse()).isEqualTo(result.getSanitizedPayload().get("message"));
    }
}

