package com.ai.infrastructure.chat.it.realapi;

import com.ai.infrastructure.chat.it.ChatSessionIntegrationTestApplication;
import com.ai.infrastructure.chat.it.actions.SafeEmptyListReadActionHandler;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("realapi")
class ChatSessionReadProbeFallbackRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Test
    void shouldFallbackToRagWhenReadActionReturnsEmptyListPayload() {
        String ownerId = "read-probe-user-" + UUID.randomUUID();
        String conversationId = "chat-" + UUID.randomUUID();

        // Seed at least one vector so the knowledge base overview exposes at least one entity type,
        // allowing read-probe fallback to execute RAG deterministically.
        vectorDatabaseService.storeVector(
            "ragconversation",
            "seed-" + UUID.randomUUID(),
            "Seed document content.",
            unitEmbedding(384),
            Map.of("source", "test")
        );

        OrchestrationContext ctx = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .position("support")
            .build();

        OrchestrationResult result = orchestrator.orchestrate(
            "Use the action '" + SafeEmptyListReadActionHandler.ACTION_NAME + "'.",
            ctx
        );

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData()).containsKey("ragResponse");

        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata()).containsKey("readProbeFallback");

        Object readProbe = result.getMetadata().get("readProbeFallback");
        assertThat(readProbe).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> probe = (Map<String, Object>) readProbe;
        assertThat(probe.get("enabled")).isEqualTo(true);
        assertThat(probe.get("action")).isEqualTo(SafeEmptyListReadActionHandler.ACTION_NAME);
        assertThat(probe.get("reason")).isEqualTo("EMPTY_READ_RESULT");
        assertThat(probe.get("vectorSpaces")).isInstanceOf(List.class);
    }

    private static List<Double> unitEmbedding(int dims) {
        List<Double> embedding = new ArrayList<>(Math.max(1, dims));
        if (dims <= 0) {
            embedding.add(1.0);
            return embedding;
        }
        embedding.add(1.0);
        for (int i = 1; i < dims; i++) {
            embedding.add(0.0);
        }
        return embedding;
    }
}
