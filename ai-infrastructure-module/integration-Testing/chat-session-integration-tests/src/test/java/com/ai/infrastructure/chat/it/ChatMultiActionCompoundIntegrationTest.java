package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.it.actions.SafeEchoActionHandler;
import com.ai.infrastructure.chat.it.actions.SafeUpperEchoActionHandler;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.extraction.IntentExtractionInput;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "ai.intent-extraction.progressive.enabled=false"
    }
)
@ActiveProfiles("test")
class ChatMultiActionCompoundIntegrationTest {

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
    void shouldHandleTwoActionsAsCompoundAndRecordTurn() {
        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(
                Intent.builder()
                    .type(IntentType.ACTION)
                    .intent(SafeEchoActionHandler.ACTION_NAME)
                    .action(SafeEchoActionHandler.ACTION_NAME)
                    .confidence(0.9)
                    .actionParams(Map.of("message", "hello"))
                    .build(),
                Intent.builder()
                    .type(IntentType.ACTION)
                    .intent(SafeUpperEchoActionHandler.ACTION_NAME)
                    .action(SafeUpperEchoActionHandler.ACTION_NAME)
                    .confidence(0.9)
                    .actionParams(Map.of("message", "world"))
                    .build()
            ))
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        when(intentQueryExtractor.extract(
            any(IntentExtractionInput.class),
            any(OrchestrationContext.class)
        )).thenReturn(response);

        String ownerId = "chat-multi-action-user";
        String conversationId = "conv-" + UUID.randomUUID();
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult result = pipeline.execute("Run both safe actions.", orch);

        assertThat(result).isNotNull();
        // Normalization promotes COMPOUND_HANDLED to the primary child type (typically ACTION_EXECUTED).
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.getChildren()).hasSize(2);
        assertThat(result.getChildren().getFirst().getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.getChildren().get(1).getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).hasSize(1);
        assertThat(session.getTurns().getFirst().getAiResponse()).isNotBlank();
    }
}
