package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.PIIDetection;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.action.ActionPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    private static final String TURN_META_KEY_RESULT_TYPE = "_resultType";
    private static final String TURN_META_KEY_ACTION = "_action";
    private static final String TURN_META_KEY_ACTION_SUCCESS = "_actionSuccess";
    private static final String TURN_META_KEY_ACTION_REFS = "_actionRefs";

    private static final int ACTION_REFS_MAX_FIELDS = 12;
    private static final int ACTION_REFS_MAX_STRING_LENGTH = 120;

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
            Map<String, Object> turnMetadata = buildTurnMetadata(context);
            chatSessionService.recordTurn(conversationId, ownerId, userQuery, assistantResponse, turnMetadata);
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

    private Map<String, Object> buildTurnMetadata(PipelineContext context) {
        if (context == null) {
            return Map.of();
        }

        OrchestrationResult result = context.getIntentResult();
        if (result == null || result.getType() == null) {
            return Map.of();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(TURN_META_KEY_RESULT_TYPE, result.getType().name());

        if (result.getType() == OrchestrationResultType.ACTION_EXECUTED
            || result.getType() == OrchestrationResultType.ACTION_DENIED
            || result.getType() == OrchestrationResultType.ERROR) {
            Map<String, Object> data = result.getData();
            if (data != null && !data.isEmpty()) {
                Object action = data.get("action");
                if (action instanceof String actionName && StringUtils.hasText(actionName)) {
                    metadata.put(TURN_META_KEY_ACTION, actionName);
                }

                ActionResult actionResult = coerceActionResult(data.get("actionResult"));
                if (actionResult != null) {
                    metadata.put(TURN_META_KEY_ACTION_SUCCESS, actionResult.isSuccess());
                    Map<String, Object> actionRefs = extractActionRefs(actionResult.getData());
                    if (!actionRefs.isEmpty()) {
                        metadata.put(TURN_META_KEY_ACTION_REFS, actionRefs);
                    }
                }
            }
        }

        return Collections.unmodifiableMap(metadata);
    }

    private ActionResult coerceActionResult(Object value) {
        if (value instanceof ActionResult result) {
            return result;
        }
        return null;
    }

    private Map<String, Object> extractActionRefs(ActionPayload payload) {
        if (payload == null) {
            return Map.of();
        }

        Map<String, Object> map;
        try {
            map = payload.toMap();
        } catch (Exception ex) {
            return Map.of();
        }

        if (map == null || map.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> refs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (refs.size() >= ACTION_REFS_MAX_FIELDS) {
                break;
            }

            if (entry == null || !StringUtils.hasText(entry.getKey())) {
                continue;
            }

            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            if (value instanceof Number || value instanceof Boolean) {
                refs.put(entry.getKey(), value);
                continue;
            }

            if (value instanceof String text) {
                String trimmed = text.trim();
                if (isSafeRefString(trimmed)) {
                    refs.put(entry.getKey(), trimmed);
                }
            }
        }

        return Collections.unmodifiableMap(refs);
    }

    private boolean isSafeRefString(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.length() > ACTION_REFS_MAX_STRING_LENGTH) {
            return false;
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return false;
        }
        // Avoid storing probable PII (emails) and free-form text in prompt context.
        if (value.indexOf('@') >= 0) {
            return false;
        }
        // Treat identifier-like strings as safe; free-form text with spaces is not.
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
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
