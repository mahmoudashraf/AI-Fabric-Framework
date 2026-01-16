package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.it.actions.SafeEchoActionHandler;
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
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.dto.AISecurityRequest;
import com.ai.infrastructure.dto.AISecurityResponse;
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
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "ai.intent-extraction.progressive.enabled=false"
    }
)
@ActiveProfiles("test")
class ChatAnonymousActionDeniedIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private Pipeline pipeline;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private AISecurityService securityService;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
    }

    @Test
    void shouldDenyAnonymousActionAndRecordTurn() {
        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder()
                .type(IntentType.ACTION)
                .intent(SafeEchoActionHandler.ACTION_NAME)
                .action(SafeEchoActionHandler.ACTION_NAME)
                .confidence(0.9)
                .actionParams(Map.of("message", "hello"))
                .build()))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .build();

        when(intentQueryExtractor.extract(
            anyString(),
            any(com.ai.infrastructure.intent.orchestration.OrchestrationContext.class)
        )).thenReturn(response);

        String sessionId = "anon-session-" + UUID.randomUUID();
        String conversationId = "conv-" + UUID.randomUUID();

        AISecurityResponse security = securityService.analyzeRequest(AISecurityRequest.builder()
            .requestId("security-" + UUID.randomUUID())
            .sessionId(sessionId)
            .content("Please echo hello")
            .operationType("INTENT_QUERY")
            .build());

        assertThat(security.getShouldBlock())
            .withFailMessage("Security unexpectedly blocked anonymous request: threats=%s error=%s",
                security.getThreatsDetected(),
                security.getErrorMessage())
            .isFalse();

        OrchestrationContext orch = OrchestrationContext.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult result = pipeline.execute("Please echo hello", orch);

        assertThat(result).isNotNull();
        assertThat(result.getType())
            .withFailMessage("Unexpected type=%s success=%s errorCode=%s message=%s dataKeys=%s",
                result.getType(),
                result.isSuccess(),
                result.getErrorCode(),
                result.getMessage(),
                result.getData() != null ? result.getData().keySet() : null)
            .isEqualTo(OrchestrationResultType.ACTION_DENIED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getSanitizedPayload()).isNotEmpty();

        ChatSession session = chatSessionService.getSession(conversationId, sessionId);
        assertThat(session.getTurns()).hasSize(1);
        assertThat(session.getTurns().getFirst().getAiResponse()).isEqualTo(result.getSanitizedPayload().get("message"));
    }
}
