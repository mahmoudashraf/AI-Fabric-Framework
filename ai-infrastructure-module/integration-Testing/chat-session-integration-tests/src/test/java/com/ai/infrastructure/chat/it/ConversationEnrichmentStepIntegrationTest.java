package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.pipeline.ConversationEnrichmentStep;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
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
        "ai.chat.enabled=true",
        "ai.chat.window-size=2",
        "ai.chat.max-context-chars=2000"
    }
)
@ActiveProfiles("test")
class ConversationEnrichmentStepIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ConversationEnrichmentStep enrichmentStep;

    @Autowired
    private com.ai.infrastructure.chat.repository.ChatSessionRepository chatSessionRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
    }

    @Test
    void shouldIncludeOnlyLastWindowTurnsInEnrichment() {
        String ownerId = "enrichment-user";
        String conversationId = "conv-" + UUID.randomUUID();

        chatSessionService.recordTurn(conversationId, ownerId, "Q1", "A1");
        chatSessionService.recordTurn(conversationId, ownerId, "Q2", "A2");
        chatSessionService.recordTurn(conversationId, ownerId, "Q3", "A3");

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        PipelineContext ctx = PipelineContext.from("Current question", orch);
        PipelineContext enriched = enrichmentStep.process(ctx);

        assertThat(enriched.getProcessedQuery()).contains("Conversation History:");
        assertThat(enriched.getProcessedQuery()).contains("Current Query:");

        assertThat(enriched.getProcessedQuery()).doesNotContain("User: Q1");
        assertThat(enriched.getProcessedQuery()).contains("User: Q2");
        assertThat(enriched.getProcessedQuery()).contains("User: Q3");
        assertThat(enriched.getProcessedQuery()).contains("Current question");
    }

    @Test
    void shouldTruncateEnrichedPromptToMaxContextChars() {
        String ownerId = "enrichment-user-truncate";
        String conversationId = "conv-" + UUID.randomUUID();

        chatSessionService.recordTurn(conversationId, ownerId, "Q1-" + "x".repeat(4000), "A1-" + "y".repeat(4000));
        chatSessionService.recordTurn(conversationId, ownerId, "Q2-" + "x".repeat(4000), "A2-" + "y".repeat(4000));

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        PipelineContext ctx = PipelineContext.from("z".repeat(4000) + " TAIL_SENTINEL", orch);
        PipelineContext enriched = enrichmentStep.process(ctx);

        assertThat(enriched.getProcessedQuery()).hasSize(2000);
        assertThat(enriched.getProcessedQuery()).contains("---END QUERY---");
        assertThat(enriched.getProcessedQuery()).contains("TAIL_SENTINEL");
    }

    @Test
    void shouldNoOpWhenConversationIdMissing() {
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("enrichment-user-2")
            .build();

        PipelineContext ctx = PipelineContext.from("Hello", orch);
        PipelineContext enriched = enrichmentStep.process(ctx);

        assertThat(enriched.getProcessedQuery()).isEqualTo("Hello");
    }
}
