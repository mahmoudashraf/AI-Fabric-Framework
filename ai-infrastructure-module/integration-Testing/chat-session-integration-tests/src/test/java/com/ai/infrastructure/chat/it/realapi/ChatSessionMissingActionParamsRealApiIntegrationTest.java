package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
import com.ai.infrastructure.chat.it.actions.ConfirmableEchoActionHandler;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("realapi")
class ChatSessionMissingActionParamsRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @BeforeEach
    void resetCounters() {
        ConfirmableEchoActionHandler.resetExecutions();
    }

    @Test
    void shouldReturnClarificationRequiredWhenActionIsMissingRequiredParams() {
        String ownerId = "missing-params-realapi-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        OrchestrationContext ctx = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult first = orchestrator.orchestrate(
            "Use action '" + ConfirmableEchoActionHandler.ACTION_NAME + "'.",
            ctx
        );

        assertThat(first).isNotNull();
        assertThat(first.getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(first.getMessage()).contains("message");
        assertThat(ConfirmableEchoActionHandler.getExecutionCount()).isZero();
    }
}

