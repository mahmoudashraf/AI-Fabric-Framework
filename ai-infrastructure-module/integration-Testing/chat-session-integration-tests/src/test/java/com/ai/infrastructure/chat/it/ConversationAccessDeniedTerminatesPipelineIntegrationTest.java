package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.pipeline.ConversationEnrichmentStep;
import com.ai.infrastructure.chat.pipeline.ConversationRecordingStep;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.spi.ChatSessionAccessControlPolicy;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@org.springframework.context.annotation.Import(ConversationAccessDeniedTerminatesPipelineIntegrationTest.DenyAccessConfig.class)
@ActiveProfiles("test")
class ConversationAccessDeniedTerminatesPipelineIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ConversationEnrichmentStep enrichmentStep;

    @Autowired
    private ConversationRecordingStep recordingStep;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
    }

    @Test
    void shouldTerminateWithAccessDeniedAndSkipRecording() {
        String ownerId = "deny-user";
        String conversationId = "conv-" + UUID.randomUUID();

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId(ownerId)
            .conversationId(conversationId)
            .build();

        PipelineContext ctx = PipelineContext.from("Hello", orch);
        PipelineContext enriched = enrichmentStep.process(ctx);

        assertThat(enriched.isShouldTerminate()).isTrue();
        assertThat(enriched.getEarlyTerminationResult()).isNotNull();
        assertThat(enriched.getEarlyTerminationResult().getType()).isEqualTo(OrchestrationResultType.ERROR);
        assertThat(enriched.getEarlyTerminationResult().getErrorCode()).isEqualTo("ACCESS_DENIED");

        recordingStep.process(enriched);
        assertThat(chatSessionRepository.findById(conversationId)).isEmpty();
    }

    @TestConfiguration
    static class DenyAccessConfig {
        @Bean
        @Primary
        ChatSessionAccessControlPolicy denyAllChatSessionAccessControlPolicy() {
            return new ChatSessionAccessControlPolicy() {
                @Override
                public boolean canCreateConversation(String ownerId) {
                    return false;
                }

                @Override
                public boolean canAccessConversation(String requestingUserId, String conversationId) {
                    return false;
                }

                @Override
                public boolean canRecordTurn(String requestingUserId, String conversationId) {
                    return false;
                }

                @Override
                public boolean canDeleteConversation(String requestingUserId, String conversationId) {
                    return false;
                }
            };
        }
    }
}
