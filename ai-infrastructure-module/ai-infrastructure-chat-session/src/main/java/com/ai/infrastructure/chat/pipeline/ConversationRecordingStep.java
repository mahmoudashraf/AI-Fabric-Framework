package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.PIIDetection;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Records a conversation turn after response sanitization.
 *
 * <p><strong>Order:</strong> 95 (after ResponseSanitizationStep (90), before HistoryPersistenceStep (100))</p>
 */
@Slf4j
@RequiredArgsConstructor
public class ConversationRecordingStep implements PipelineStep {

    private static final String STEP_NAME = "ConversationRecording";
    private static final int STEP_ORDER = 95;

    private final ChatSessionService chatSessionService;
    private final ChatSessionProperties properties;
    private final ObjectProvider<PIIDetectionService> piiDetectionService;

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public boolean shouldSkip(PipelineContext context) {
        if (context == null) {
            return true;
        }
        if (!context.isShouldTerminate()) {
            return false;
        }
        return !shouldRecordAfterTermination(context);
    }

    @Override
    public PipelineContext process(PipelineContext context) {
        if (context == null) {
            return context;
        }
        if (context.isShouldTerminate() && !shouldRecordAfterTermination(context)) {
            return context;
        }
        if (properties == null || !properties.isEnabled()) {
            return context;
        }
        if (context.getOrchestrationContext() == null || !context.getOrchestrationContext().hasConversation()) {
            return context;
        }
        if (context.getIntentResult() == null) {
            return context;
        }

        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(ownerId)) {
            return context;
        }

        String userQuery = redactIfPossible(context.getOriginalQuery());
        String assistantResponse = sanitizedMessage(context);
        if (!StringUtils.hasText(userQuery) || !StringUtils.hasText(assistantResponse)) {
            return context;
        }

        try {
            chatSessionService.recordTurn(conversationId, ownerId, userQuery, assistantResponse);
        } catch (Exception ex) {
            log.warn("Failed to record conversation turn conversationId={}: {}", conversationId, ex.getMessage());
        }
        return context;
    }

    private boolean shouldRecordAfterTermination(PipelineContext context) {
        if (context == null) {
            return false;
        }
        if (!context.isShouldTerminate()) {
            return true;
        }
        if (context.getEarlyTerminationResult() == null || context.getEarlyTerminationResult().getType() == null) {
            return false;
        }
        // Clarification is an expected user-facing outcome; keep the conversation history consistent.
        return context.getEarlyTerminationResult().getType() == com.ai.infrastructure.intent.orchestration.OrchestrationResultType.CLARIFICATION_REQUIRED;
    }

    private String sanitizedMessage(PipelineContext context) {
        Map<String, Object> sanitizedPayload = context.getSanitizedPayload();
        if (sanitizedPayload != null) {
            Object message = sanitizedPayload.get("message");
            if (message instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }
        return context.getIntentResult() != null ? context.getIntentResult().getMessage() : null;
    }

    private String redactIfPossible(String originalQuery) {
        if (!StringUtils.hasText(originalQuery)) {
            return originalQuery;
        }
        PIIDetectionService service = piiDetectionService != null ? piiDetectionService.getIfAvailable() : null;
        if (service == null) {
            return originalQuery;
        }

        PIIDetectionResult analysis;
        try {
            analysis = service.analyze(originalQuery);
        } catch (Exception ex) {
            return originalQuery;
        }

        if (analysis == null || !analysis.isPiiDetected()) {
            return originalQuery;
        }

        String processed = analysis.getProcessedQuery();
        if (StringUtils.hasText(processed) && !processed.equals(originalQuery)) {
            return processed;
        }

        if (analysis.getDetections() == null || analysis.getDetections().isEmpty()) {
            return originalQuery;
        }

        return redact(originalQuery, analysis.getDetections());
    }

    private String redact(String original, List<PIIDetection> detections) {
        if (!StringUtils.hasText(original) || detections == null || detections.isEmpty()) {
            return original;
        }

        StringBuilder builder = new StringBuilder(original);
        detections.stream()
            .filter(d -> d != null && StringUtils.hasText(d.getMaskedValue()))
            .sorted(Comparator.comparingInt((PIIDetection d) -> d.getStartIndex()).reversed())
            .forEach(detection -> {
                int start = Math.max(0, Math.min(detection.getStartIndex(), builder.length()));
                int end = Math.max(start, Math.min(detection.getEndIndex(), builder.length()));
                builder.replace(start, end, detection.getMaskedValue());
            });
        return builder.toString();
    }
}
