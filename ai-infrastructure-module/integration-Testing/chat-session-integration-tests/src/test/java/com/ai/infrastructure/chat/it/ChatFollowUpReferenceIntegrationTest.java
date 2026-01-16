package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.Pipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "ai.intent-extraction.progressive.enabled=false"
    }
)
@ActiveProfiles("test")
class ChatFollowUpReferenceIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private Pipeline pipeline;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
    }

    @Test
    void shouldIncludeConversationHistoryInSecondTurnIntentExtraction() {
        MultiIntentResponse outOfScope = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder()
                .type(IntentType.OUT_OF_SCOPE)
                .intent("out_of_scope")
                .confidence(0.9)
                .build()))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        when(intentQueryExtractor.extract(
            anyString(),
            any(com.ai.infrastructure.intent.orchestration.OrchestrationContext.class)
        )).thenReturn(outOfScope);

        String ownerId = "chat-followup-user";
        String conversationId = "conv-" + UUID.randomUUID();
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        pipeline.execute("I want to echo hello.", orch);
        pipeline.execute("Do it.", orch);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(intentQueryExtractor, times(2)).extract(
            promptCaptor.capture(),
            any(com.ai.infrastructure.intent.orchestration.OrchestrationContext.class)
        );

        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts).hasSize(2);
        assertThat(prompts.getFirst()).isEqualTo("I want to echo hello.");

        String secondPrompt = prompts.get(1);
        assertThat(secondPrompt).contains("Conversation History:");
        assertThat(secondPrompt).contains("User: I want to echo hello.");
        assertThat(secondPrompt).contains("Assistant:");
        assertThat(secondPrompt).contains("Current Query:");
        assertThat(secondPrompt).contains("Do it.");
    }
}
