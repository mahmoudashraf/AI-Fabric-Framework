package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.pipeline.ConversationEnrichmentStep;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class ConversationOwnerMismatchDeniedIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ConversationEnrichmentStep enrichmentStep;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
    }

    @Test
    void shouldFailClosedWhenConversationOwnedByDifferentUser() {
        String ownerId = "owner-user";
        String otherUserId = "other-user";
        String conversationId = "conv-" + UUID.randomUUID();

        chatSessionService.recordTurn(conversationId, ownerId, "Hello", "Hi", Map.of(), Map.of());
        ChatSession owned = chatSessionService.getSession(conversationId, ownerId);
        assertThat(owned.getTurns()).hasSize(1);

        OrchestrationContext otherCtx = OrchestrationContext.builder()
            .userId(otherUserId)
            .conversationId(conversationId)
            .build();

        PipelineContext ctx = PipelineContext.from("What did I say?", otherCtx);
        PipelineContext enriched = enrichmentStep.process(ctx);

        assertThat(enriched.isShouldTerminate()).isTrue();
        assertThat(enriched.getEarlyTerminationResult()).isNotNull();
        assertThat(enriched.getEarlyTerminationResult().getType()).isEqualTo(OrchestrationResultType.ERROR);
        assertThat(enriched.getEarlyTerminationResult().getErrorCode()).isEqualTo("ACCESS_DENIED");

        assertThat(chatSessionRepository.findById(conversationId)).isPresent();
        ChatSession stillOwned = chatSessionService.getSession(conversationId, ownerId);
        assertThat(stillOwned.getTurns()).hasSize(1);
    }
}
