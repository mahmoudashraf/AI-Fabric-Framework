package com.ai.infrastructure.it;

import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.it.support.RealAPITestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPIChatSessionIntegrationTest {

    private static final String OPENAI_KEY_PROPERTY = "OPENAI_API_KEY";

    static {
        RealAPITestSupport.ensureOpenAIConfigured();
        System.setProperty("LLM_PROVIDER", System.getProperty("LLM_PROVIDER", "openai"));
        System.setProperty("ai.providers.llm-provider", System.getProperty("ai.providers.llm-provider", "openai"));
        System.setProperty("EMBEDDING_PROVIDER", System.getProperty("EMBEDDING_PROVIDER", "onnx"));
        System.setProperty("ai.providers.embedding-provider", System.getProperty("ai.providers.embedding-provider", "onnx"));
    }

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        chatSessionRepository.deleteAll();
    }

    @Test
    void shouldRecordAndEnrichConversationAcrossTurns() {
        assumeOpenAIConfigured();

        String conversationId = "realapi-chat-" + UUID.randomUUID();
        String ownerId = "realapi-user";

        OrchestrationContext firstContext = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult first = orchestrator.orchestrate(
            "Give me a one-line summary of the AI Fabric platform.",
            firstContext
        );

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getMessage()).isNotBlank();

        OrchestrationContext secondContext = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        OrchestrationResult second = orchestrator.orchestrate(
            "List two standout features of that platform.",
            secondContext
        );

        assertThat(second.isSuccess()).isTrue();
        assertThat(second.getMessage()).isNotBlank();

        // Verify the conversation persisted and is owned correctly
        var session = chatSessionRepository.findById(conversationId)
            .orElseThrow(() -> new AssertionError("Conversation should be persisted"));
        assertThat(session.getOwnerId()).isEqualTo(ownerId);
        assertThat(session.getTurns()).hasSizeGreaterThanOrEqualTo(2);

        // Verify history enrichment contains both user queries
        String history = chatSessionService.getConversationContext(conversationId, ownerId);
        assertThat(history).contains("one-line summary of the AI Fabric platform");
        assertThat(history).contains("List two standout features of that platform");
    }

    private void assumeOpenAIConfigured() {
        String apiKey = System.getProperty(OPENAI_KEY_PROPERTY);
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getenv(OPENAI_KEY_PROPERTY);
        }
        Assumptions.assumeTrue(StringUtils.hasText(apiKey),
            "OPENAI_API_KEY not configured; skipping Real API chat session test.");
    }
}
