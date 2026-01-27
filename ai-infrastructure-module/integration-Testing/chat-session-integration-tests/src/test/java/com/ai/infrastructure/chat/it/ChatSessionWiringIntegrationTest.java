package com.ai.infrastructure.chat.it;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.pipeline.ConversationEnrichmentStep;
import com.ai.infrastructure.chat.pipeline.ConversationRecordingStep;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.pipeline.Pipeline;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ChatSessionIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class ChatSessionWiringIntegrationTest {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private Pipeline pipeline;

    @Autowired
    private ConversationEnrichmentStep conversationEnrichmentStep;

    @Autowired
    private ConversationRecordingStep conversationRecordingStep;

    @Test
    void shouldExposeConversationPipelineStepsWhenEnabled() {
        List<String> stepNames = pipeline.getSteps().stream()
            .map(PipelineStep::getStepName)
            .toList();

        assertThat(stepNames).contains("ConversationEnrichment", "ConversationRecording");
        assertThat(conversationEnrichmentStep.getOrder()).isEqualTo(25);
        assertThat(conversationRecordingStep.getOrder()).isEqualTo(95);
    }

    @Test
    void shouldPersistTurnAndBuildConversationContext() {
        String ownerId = "chat-it-user";
        String conversationId = "conv-" + UUID.randomUUID();

        chatSessionService.recordTurn(conversationId, ownerId, "Hello", "Hi there", Map.of(
            "_action", "create_purchase_order",
            "_actionSuccess", true,
            "_actionRefs", Map.of(
                "orderNumber", "PO-123",
                "orderId", 9,
                "sku", "SKU-ABC-1"
            )
        ));

        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        assertThat(session.getTurns()).hasSize(1);

        String context = chatSessionService.getConversationContext(conversationId, ownerId);
        assertThat(context).contains("User: Hello");
        assertThat(context).contains("Assistant: Hi there");
        assertThat(context).contains("Action Context:");
        assertThat(context).contains("action=create_purchase_order");
        assertThat(context).contains("success=true");
        assertThat(context).contains("orderNumber=PO-123");
        assertThat(context).contains("orderId=9");
        assertThat(context).contains("sku=SKU-ABC-1");
    }
}
