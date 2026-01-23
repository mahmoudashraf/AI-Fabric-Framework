package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.it.actions.ConfirmableEchoActionHandler;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.util.ActionDraftMetadata;
import com.ai.infrastructure.chat.util.ConfirmationStack;
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
import java.util.Map;
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
class ChatActionMissingParamsClarificationIntegrationTest {

    @Autowired
    private Pipeline pipeline;

    @Autowired
    private ChatSessionService chatSessionService;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @BeforeEach
    void reset() {
        ConfirmableEchoActionHandler.resetExecutions();
    }

    @Test
    void shouldAskForMissingRequiredParamsThenConfirmAndExecute() {
        MultiIntentResponse missingParams = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder()
                .type(IntentType.ACTION)
                .action(ConfirmableEchoActionHandler.ACTION_NAME)
                .actionParams(Map.of())
                .confidence(0.9)
                .build()))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        MultiIntentResponse completedParams = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder()
                .type(IntentType.ACTION)
                .action(ConfirmableEchoActionHandler.ACTION_NAME)
                .actionParams(Map.of("message", "hello"))
                .confidence(0.9)
                .build()))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        MultiIntentResponse confirmYes = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder()
                .type(IntentType.CONFIRMATION_POSITIVE)
                .intent("confirm")
                .confidence(1.0)
                .build()))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        when(intentQueryExtractor.extract(anyString(), any(OrchestrationContext.class)))
            .thenReturn(missingParams, completedParams, confirmYes);

        String ownerId = "missing-params-user";
        String conversationId = "chat-" + UUID.randomUUID();
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult first = pipeline.execute("i want to buy this", orch);
        assertThat(first).isNotNull();
        assertThat(first.getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(first.getMessage()).contains("message");
        assertThat(ConfirmableEchoActionHandler.getExecutionCount()).isZero();

        ChatSession sessionAfterFirst = chatSessionService.getSession(conversationId, ownerId);
        assertThat(sessionAfterFirst.getSessionMetadata()).isNotNull();
        assertThat(sessionAfterFirst.getSessionMetadata().get(ActionDraftMetadata.METADATA_KEY_DRAFT)).isNotNull();

        OrchestrationResult second = pipeline.execute("message=hello", orch);
        assertThat(second).isNotNull();
        assertThat(second.getType()).isEqualTo(OrchestrationResultType.CONFIRMATION_REQUIRED);
        assertThat(ConfirmableEchoActionHandler.getExecutionCount()).isZero();

        ChatSession sessionAfterSecond = chatSessionService.getSession(conversationId, ownerId);
        assertThat(sessionAfterSecond.getSessionMetadata()).isNotNull();
        assertThat(sessionAfterSecond.getSessionMetadata().get(ActionDraftMetadata.METADATA_KEY_DRAFT)).isNull();
        assertThat(sessionAfterSecond.getSessionMetadata().get(ConfirmationStack.METADATA_KEY_STACK)).isNotNull();

        OrchestrationResult third = pipeline.execute("yes", orch);
        assertThat(third).isNotNull();
        assertThat(third.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(third.isSuccess()).isTrue();
        assertThat(ConfirmableEchoActionHandler.getExecutionCount()).isEqualTo(1);

        ChatSession sessionAfterThird = chatSessionService.getSession(conversationId, ownerId);
        assertThat(sessionAfterThird.getSessionMetadata()).isNotNull();
        Object confirmationRaw = sessionAfterThird.getSessionMetadata().get(ConfirmationStack.METADATA_KEY_STACK);
        if (confirmationRaw instanceof List<?> list) {
            assertThat(list).isEmpty();
        } else {
            assertThat(confirmationRaw).isNull();
        }
    }
}
