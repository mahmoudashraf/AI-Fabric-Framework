package com.ai.infrastructure.integration;

import com.ai.infrastructure.config.TestConfiguration;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.entity.IntentHistory;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.repository.IntentHistoryRepository;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(classes = TestConfiguration.class)
@ActiveProfiles("test")
class RAGIntegrationFlowTest {

    private static final String ACTION_QUERY = "Please clear my index and remove card 4111-1111-1111-1111.";
    private static final String INFO_QUERY = "What's your refund policy?";
    private static final String COMPOUND_QUERY = "Pause my subscription and show special offers.";
    private static final String OOS_QUERY = "Build me a spaceship!";

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private IntentHistoryRepository historyRepository;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @MockBean
    private IntentQueryExtractor intentQueryExtractor;

    @SpyBean(name = "ragService")
    private RAGService ragService;

    @BeforeEach
    void resetState() {
        vectorDatabaseService.clearVectors();
        historyRepository.deleteAll();
    }

    @Test
    void shouldHandleActionFlowAndPersistHistory() {
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder()
                .intents(List.of(Intent.builder()
                    .type(IntentType.ACTION)
                    .action("clear_vector_index")
                    .confidence(0.95)
                    .nextStepRecommended(NextStepRecommendation.builder()
                        .intent("show_refund_timeline")
                        .query("What is the refund timeline?")
                        .confidence(0.8)
                        .build())
                    .build()))
                .metadata(Map.of("sessionId", "session-action"))
                .build());

        vectorDatabaseService.storeVector("doc", "123", "content", List.of(0.1, 0.2), Map.of());

        OrchestrationResult result = orchestrator.orchestrate(ACTION_QUERY, "user-action");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSanitizedPayload()).isNotEmpty();
        assertThat(result.getSanitizedPayload().get("message")).isNotNull();

        assertRecordedHistory("user-action", 1);

        var history = historyRepository.findByUserIdOrderByCreatedAtDesc("user-action").getFirst();
        assertThat(history.getRedactedQuery()).doesNotContain("4111-1111-1111-1111");
        assertThat(history.getSensitiveDataTypes()).contains("CREDIT_CARD");
        assertThat(history.getIntentCount()).isEqualTo(1);
        assertThat(history.getExecutionStatus()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED.name());
    }

    @Test
    void shouldHandleInformationFlowAndPersistHistory() {
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder()
                .intents(List.of(Intent.builder()
                    .type(IntentType.INFORMATION)
                    .intent("show_refund_policy")
                    .confidence(0.9)
                    .vectorSpace("policies")
                    .build()))
                .build());

        doReturn(RAGResponse.builder()
            .response("Refunds are processed within 5 business days.")
            .documents(List.of())
            .success(true)
            .build()).when(ragService).performRag(any(RAGRequest.class));

        OrchestrationResult result = orchestrator.orchestrate(INFO_QUERY, "user-info");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getSanitizedPayload()).isNotEmpty();

        assertRecordedHistory("user-info", 1);
        var history = historyRepository.findByUserIdOrderByCreatedAtDesc("user-info").getFirst();
        assertThat(history.getRedactedQuery()).isEqualTo(INFO_QUERY);
        assertThat(history.getIntentCount()).isEqualTo(1);
        assertThat(history.getResultJson()).contains("Refunds are processed");
    }

    @Test
    void shouldHandleCompoundFlowAndPersistHistory() {
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder()
                .intents(List.of(
                    Intent.builder()
                        .type(IntentType.ACTION)
                        .action("clear_vector_index")
                        .confidence(0.9)
                        .build(),
                    Intent.builder()
                        .type(IntentType.INFORMATION)
                        .intent("show_special_offers")
                        .confidence(0.85)
                        .vectorSpace("offers")
                        .build()))
                .compound(true)
                .build());

        doReturn(RAGResponse.builder().response("Here are current offers.").build())
            .when(ragService).performRag(any(RAGRequest.class));

        OrchestrationResult result = orchestrator.orchestrate(COMPOUND_QUERY, "user-compound");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.COMPOUND_HANDLED);
        assertRecordedHistory("user-compound", 1);

        IntentHistory history = historyRepository.findByUserIdOrderByCreatedAtDesc("user-compound").getFirst();
        assertThat(history.getIntentCount()).isEqualTo(2);
        assertThat(history.getResultJson()).contains("COMPOUND_HANDLED");
        assertThat(history.getRedactedQuery()).contains("Pause my subscription");
    }

    @Test
    void shouldHandleOutOfScopeAndPersistHistory() {
        when(intentQueryExtractor.extract(any(), any()))
            .thenReturn(MultiIntentResponse.builder()
                .intents(List.of(Intent.builder()
                    .type(IntentType.OUT_OF_SCOPE)
                    .intent("out_of_scope")
                    .confidence(0.6)
                    .build()))
                .build());

        OrchestrationResult result = orchestrator.orchestrate(OOS_QUERY, "user-oos");

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.OUT_OF_SCOPE);
        assertRecordedHistory("user-oos", 1);

        IntentHistory history = historyRepository.findByUserIdOrderByCreatedAtDesc("user-oos").getFirst();
        assertThat(history.getExecutionStatus()).isEqualTo(OrchestrationResultType.OUT_OF_SCOPE.name());
        assertThat(history.getRedactedQuery()).isEqualTo(OOS_QUERY);
    }

    private void assertRecordedHistory(String userId, int expectedCount) {
        List<IntentHistory> history = historyRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(history).hasSize(expectedCount);
        assertThat(history.getFirst().getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(history.getFirst().getExpiresAt()).isAfter(LocalDateTime.now());
    }
}
