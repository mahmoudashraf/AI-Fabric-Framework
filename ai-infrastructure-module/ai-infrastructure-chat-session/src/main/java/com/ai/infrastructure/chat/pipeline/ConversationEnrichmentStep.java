package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.exception.ChatSessionAccessDeniedException;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pipeline step that enriches the processed query with conversation history.
 *
 * <p><strong>Order:</strong> 25 (after access control, before PII detection)</p>
 */
@Slf4j
@RequiredArgsConstructor
public class ConversationEnrichmentStep implements PipelineStep {

    private static final String STEP_NAME = "ConversationEnrichment";
    private static final int STEP_ORDER = 25;

    private static final String METADATA_KEY_CHAT = "chat";
    private static final String ERROR_CODE_ACCESS_DENIED = "ACCESS_DENIED";

    private final ChatSessionService chatSessionService;
    private final ChatSessionProperties properties;

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public PipelineContext process(PipelineContext context) {
        if (context == null || context.isShouldTerminate()) {
            return context;
        }
        if (context.getOrchestrationContext() == null || !context.getOrchestrationContext().hasConversation()) {
            return context;
        }
        if (properties == null || !properties.isEnabled()) {
            return context;
        }

        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(ownerId)) {
            return context;
        }

        try {
            String history = chatSessionService.getConversationContext(conversationId, ownerId);
            if (!StringUtils.hasText(history)) {
                return context.withMetadata(METADATA_KEY_CHAT, Map.of(
                    "conversationId", conversationId,
                    "historyChars", 0
                ));
            }

            String currentQuery = context.getEffectiveQuery();
            String enriched = """
                Conversation History:
                ---BEGIN HISTORY---
                %s
                ---END HISTORY---

                Current Query:
                ---BEGIN QUERY---
                %s
                ---END QUERY---
                """.formatted(history, currentQuery != null ? currentQuery : "");

            int maxChars = properties.getMaxContextChars();
            if (enriched.length() > maxChars) {
                enriched = enriched.substring(enriched.length() - maxChars);
            }

            Map<String, Object> chatMeta = new LinkedHashMap<>();
            chatMeta.put("conversationId", conversationId);
            chatMeta.put("historyChars", history.length());
            chatMeta.put("memoryStrategy", properties.getMemoryStrategy() != null ? properties.getMemoryStrategy().name() : null);
            chatMeta.put("windowSize", properties.getWindowSize());

            return context.toBuilder()
                .processedQuery(enriched)
                .metadata(mergeMetadata(context.getMetadata(), Map.of(METADATA_KEY_CHAT, chatMeta)))
                .build();
        } catch (ChatSessionAccessDeniedException ex) {
            OrchestrationResult denied = OrchestrationResult.builder()
                .type(com.ai.infrastructure.intent.orchestration.OrchestrationResultType.ERROR)
                .success(false)
                .errorCode(ERROR_CODE_ACCESS_DENIED)
                .message("Access denied to conversation")
                .build();
            return context.terminate(denied);
        } catch (Exception ex) {
            log.warn("Failed to enrich conversation {}: {}", conversationId, ex.getMessage());
            return context;
        }
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> base, Map<String, Object> additions) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null && !base.isEmpty()) {
            merged.putAll(base);
        }
        if (additions != null && !additions.isEmpty()) {
            merged.putAll(additions);
        }
        return Collections.unmodifiableMap(merged);
    }
}
