package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.PIIDetection;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionListPayload;
import com.ai.infrastructure.intent.action.ActionPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.ActionTargetRef;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
    private static final String TURN_META_KEY_WORKING_SET = "_workingSet";

    private static final String RESULT_DATA_KEY_ACTION_RESULT = "actionResult";
    private static final String TARGET_REF_KEY_ID = "id";
    private static final String TARGET_REF_KEY_VECTOR_SPACE = "vectorSpace";
    private static final String TARGET_REF_KEY_CONTENT_TEXT = "contentText";
    private static final String TARGET_REF_KEY_METADATA = "metadata";

    private static final String SESSION_META_KEY_LAST_RESOLVED_TARGETS = "lastResolvedTargets";
    private static final String SESSION_META_KEY_LAST_RESOLVED_TARGETS_TURN_INDEX = "lastResolvedTargetsTurnIndex";

    private static final int ACTION_REFS_MAX_FIELDS = 12;
    private static final int ACTION_REFS_MAX_STRING_LENGTH = 120;
    private static final int WORKING_SET_MAX_DOCS = 8;
    private static final int RESOLVED_TARGETS_MAX = 8;
    private static final int UI_METADATA_MAX_ENTRIES = 24;
    private static final int UI_METADATA_MAX_KEY_LENGTH = 40;

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
            Map<String, Object> uiMetadata = extractUiMetadata(context);
            chatSessionService.recordTurn(conversationId, ownerId, userQuery, assistantResponse, turnMetadata, uiMetadata);
            persistPinnedTargets(context, conversationId, ownerId);
        } catch (Exception ex) {
            log.warn("Failed to record conversation turn conversationId={}: {}", conversationId, ex.getMessage());
        }
        return context;
    }

    private void persistPinnedTargets(PipelineContext context, String conversationId, String ownerId) {
        if (context == null || !StringUtils.hasText(conversationId) || !StringUtils.hasText(ownerId)) {
            return;
        }

        List<ResolvedTarget> targets = extractPinnedTargetsFromActionListResult(context);
        if (targets.isEmpty()) {
            targets = context.getResolvedTargets();
        }
        if (targets == null || targets.isEmpty()) {
            return;
        }

        List<Map<String, Object>> stored = new ArrayList<>();
        for (ResolvedTarget target : targets) {
            if (stored.size() >= RESOLVED_TARGETS_MAX) {
                break;
            }
            if (target == null || !StringUtils.hasText(target.getId())) {
                continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(TARGET_REF_KEY_ID, target.getId().trim());
            if (StringUtils.hasText(target.getVectorSpace())) {
                entry.put(TARGET_REF_KEY_VECTOR_SPACE, target.getVectorSpace().trim());
            }
            if (StringUtils.hasText(target.getContentText())) {
                entry.put(TARGET_REF_KEY_CONTENT_TEXT, target.getContentText());
            }
            if (target.getMetadata() != null && !target.getMetadata().isEmpty()) {
                entry.put(TARGET_REF_KEY_METADATA, new LinkedHashMap<>(target.getMetadata()));
            }
            if (target.getSource() != null) {
                entry.put("source", target.getSource().name());
            }
            stored.add(Collections.unmodifiableMap(entry));
        }

        if (stored.isEmpty()) {
            return;
        }

        int turnIndex = 0;
        try {
            var session = chatSessionService.getSession(conversationId, ownerId);
            turnIndex = session != null && session.getTurns() != null ? session.getTurns().size() : 0;
        } catch (Exception ignored) {
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(SESSION_META_KEY_LAST_RESOLVED_TARGETS, Collections.unmodifiableList(stored));
        updates.put(SESSION_META_KEY_LAST_RESOLVED_TARGETS_TURN_INDEX, turnIndex);

        try {
            chatSessionService.mergeSessionMetadata(conversationId, ownerId, updates);
        } catch (Exception ex) {
            log.debug("Failed to persist lastResolvedTargets for conversationId={}: {}", conversationId, ex.getMessage());
        }
    }

    private List<ResolvedTarget> extractPinnedTargetsFromActionListResult(PipelineContext context) {
        if (context == null || context.getIntentResult() == null || context.getIntentResult().getData() == null) {
            return List.of();
        }

        OrchestrationResult result = context.getIntentResult();
        if (result.getType() != OrchestrationResultType.ACTION_EXECUTED) {
            return List.of();
        }

        Map<String, Object> data = result.getData();
        ActionResult actionResult = coerceActionResult(data.get(RESULT_DATA_KEY_ACTION_RESULT));
        if (actionResult == null || !actionResult.isSuccess()) {
            return List.of();
        }

        ActionPayload payload = actionResult.getData();
        if (!(payload instanceof ActionListPayload listPayload)) {
            return List.of();
        }

        List<?> items = listPayload.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<ResolvedTarget> targets = new ArrayList<>();
        for (Object item : items) {
            if (targets.size() >= RESOLVED_TARGETS_MAX) {
                break;
            }

            ResolvedTarget extracted = extractTargetFromListItem(item);
            if (extracted != null) {
                targets.add(extracted);
            }
        }

        return targets.isEmpty() ? List.of() : Collections.unmodifiableList(targets);
    }

    private ResolvedTarget extractTargetFromListItem(Object item) {
        if (item == null) {
            return null;
        }

        if (item instanceof ActionTargetRef ref) {
            if (!StringUtils.hasText(ref.id())) {
                return null;
            }
            return ResolvedTarget.builder()
                .id(ref.id().trim())
                .vectorSpace(StringUtils.hasText(ref.vectorSpace()) ? ref.vectorSpace().trim() : null)
                .contentText(StringUtils.hasText(ref.contentText()) ? ref.contentText().trim() : null)
                .metadata(ref.metadata() != null ? ref.metadata() : Map.of())
                .source(com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource.ACTION_RESULT_ITEMS)
                .build();
        }

        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }

        String id = coerceToString(map.get(TARGET_REF_KEY_ID));
        if (!StringUtils.hasText(id)) {
            return null;
        }

        String vectorSpace = coerceToString(map.get(TARGET_REF_KEY_VECTOR_SPACE));
        String contentText = coerceToString(map.get(TARGET_REF_KEY_CONTENT_TEXT));
        Map<String, String> metadata = coerceToStringMap(map.get(TARGET_REF_KEY_METADATA));

        return ResolvedTarget.builder()
            .id(id.trim())
            .vectorSpace(StringUtils.hasText(vectorSpace) ? vectorSpace.trim() : null)
            .contentText(StringUtils.hasText(contentText) ? contentText.trim() : null)
            .metadata(metadata)
            .source(com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource.ACTION_RESULT_ITEMS)
            .build();
    }

    private String coerceToString(Object value) {
        if (value instanceof String str) {
            return str;
        }
        return value != null ? value.toString() : null;
    }

    private Map<String, String> coerceToStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            String val = String.valueOf(entry.getValue());
            if (!StringUtils.hasText(key) || !StringUtils.hasText(val)) {
                continue;
            }
            out.put(key, val);
        }

        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
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

        if (result.getType() == OrchestrationResultType.INFORMATION_PROVIDED) {
            Map<String, Object> workingSet = extractWorkingSet(result);
            if (!workingSet.isEmpty()) {
                metadata.put(TURN_META_KEY_WORKING_SET, workingSet);
            }
        }

        return Collections.unmodifiableMap(metadata);
    }

    private Map<String, Object> extractUiMetadata(PipelineContext context) {
        if (context == null || context.getOrchestrationContext() == null) {
            return Map.of();
        }

        Map<String, Object> raw = context.getOrchestrationContext().getUiMetadata();
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (safe.size() >= UI_METADATA_MAX_ENTRIES) {
                break;
            }
            if (entry == null || !StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }

            String key = entry.getKey().trim();
            if (key.isEmpty() || key.length() > UI_METADATA_MAX_KEY_LENGTH) {
                continue;
            }
            if (key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                safe.put(key, value);
                continue;
            }
            if (value instanceof String text) {
                String trimmed = text.trim();
                if (isSafeRefString(trimmed)) {
                    safe.put(key, trimmed);
                }
            }
        }

        return safe.isEmpty() ? Map.of() : Collections.unmodifiableMap(safe);
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

    private Map<String, Object> extractWorkingSet(OrchestrationResult result) {
        if (result == null || result.getData() == null || result.getData().isEmpty()) {
            return Map.of();
        }

        Object ragValue = result.getData().get("ragResponse");
        if (!(ragValue instanceof RAGResponse ragResponse)) {
            return Map.of();
        }

        List<RAGResponse.RAGDocument> documents = ragResponse.getDocuments();
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }

        String fallbackVectorSpace = normalizeVectorSpace(ragResponse.getEntityType());
        boolean fallbackSingle = StringUtils.hasText(fallbackVectorSpace) && fallbackVectorSpace.indexOf(',') < 0;

        List<Map<String, Object>> refs = new java.util.ArrayList<>();
        java.util.Set<String> vectorSpaces = new java.util.LinkedHashSet<>();

        for (RAGResponse.RAGDocument doc : documents) {
            if (refs.size() >= WORKING_SET_MAX_DOCS) {
                break;
            }
            if (doc == null || !StringUtils.hasText(doc.getId())) {
                continue;
            }
            String id = doc.getId().trim();
            if (!isSafeRefString(id)) {
                continue;
            }

            String vectorSpace = null;
            Map<String, Object> docMeta = doc.getMetadata();
            if (docMeta != null) {
                Object vs = docMeta.get("vectorSpace");
                if (vs instanceof String vsText) {
                    vectorSpace = normalizeVectorSpace(vsText);
                }
            }
            if (!StringUtils.hasText(vectorSpace) && fallbackSingle) {
                vectorSpace = fallbackVectorSpace;
            }

            if (StringUtils.hasText(vectorSpace) && isSafeRefString(vectorSpace)) {
                vectorSpaces.add(vectorSpace);
            } else {
                vectorSpace = null;
            }

            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("id", id);
            if (vectorSpace != null) {
                ref.put("vectorSpace", vectorSpace);
            }
            if (doc.getScore() != null) {
                ref.put("score", doc.getScore());
            }

            Map<String, Object> safeMeta = extractWorkingSetMetadata(docMeta);
            if (!safeMeta.isEmpty()) {
                ref.put("metadata", safeMeta);
            }
            refs.add(Collections.unmodifiableMap(ref));
        }

        if (refs.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> workingSet = new LinkedHashMap<>();
        workingSet.put("vectorSpacesUsed", List.copyOf(vectorSpaces));
        workingSet.put("topDocumentRefs", Collections.unmodifiableList(refs));
        workingSet.put("documentsCount", documents.size());
        return Collections.unmodifiableMap(workingSet);
    }

    private Map<String, Object> extractWorkingSetMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (safe.size() >= 8) {
                break;
            }
            if (entry == null || !StringUtils.hasText(entry.getKey())) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.isEmpty() || key.length() > 40) {
                continue;
            }
            if ("vectorSpace".equalsIgnoreCase(key)) {
                continue;
            }

            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Number || value instanceof Boolean) {
                safe.put(key, value);
                continue;
            }
            if (value instanceof String text) {
                String trimmed = text.trim();
                if (isSafeRefString(trimmed)) {
                    safe.put(key, trimmed);
                }
            }
        }

        return safe.isEmpty() ? Map.of() : Collections.unmodifiableMap(safe);
    }

    private String normalizeVectorSpace(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        trimmed = trimmed.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        return trimmed.isEmpty() ? null : trimmed;
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
