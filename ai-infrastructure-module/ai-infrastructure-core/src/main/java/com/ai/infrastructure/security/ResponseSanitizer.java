package com.ai.infrastructure.security;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.dto.PIIDetection;
import com.ai.infrastructure.dto.PIIDetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sanitises orchestrator responses before they are returned to clients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseSanitizer {

    private final PIIDetectionService piiDetectionService;
    private final ResponseSanitizationProperties properties;

    public Map<String, Object> sanitize(OrchestrationResult result, String userId) {
        if (result == null) {
            return Collections.emptyMap();
        }

        if (!properties.isEnabled()) {
            return basicPayload(result);
        }

        SanitizationOutcome<String> messageOutcome = sanitizeText(result.getMessage(), userId);
        SanitizationOutcome<Object> dataOutcome = sanitizeObject(result.getData(), userId);
        SanitizationOutcome<List<Map<String, Object>>> suggestionOutcome = sanitizeSuggestions(result, userId);
        SanitizationOutcome<Map<String, Object>> smartSuggestionOutcome =
            sanitizeMap(result.getSmartSuggestion(), userId);

        Map<String, Object> payload = new LinkedHashMap<>();
        if (result.getType() != null) {
            payload.put("type", result.getType().name());
        }
        payload.put("success", result.isSuccess());
        payload.put("message", messageOutcome.value());
        payload.put("data", normalizeData(dataOutcome.value()));
        if (!suggestionOutcome.value().isEmpty()) {
            payload.put("suggestions", Collections.unmodifiableList(suggestionOutcome.value()));
        }
        if (!smartSuggestionOutcome.value().isEmpty()) {
            payload.put("smartSuggestion", Collections.unmodifiableMap(smartSuggestionOutcome.value()));
        }

        boolean highRiskDetected = messageOutcome.highRisk()
            || dataOutcome.highRisk()
            || suggestionOutcome.highRisk()
            || smartSuggestionOutcome.highRisk();

        if (highRiskDetected) {
            payload.put("warning", properties.getHighRiskWarningMessage());
        }

        return Collections.unmodifiableMap(payload);
    }

    private Map<String, Object> basicPayload(OrchestrationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result.getType() != null) {
            payload.put("type", result.getType().name());
        }
        payload.put("success", result.isSuccess());
        payload.put("message", result.getMessage());
        payload.put("data", result.getData());
        if (!CollectionUtils.isEmpty(result.getNextSteps())) {
            payload.put("suggestions", result.getNextSteps());
        }
        if (!CollectionUtils.isEmpty(result.getSmartSuggestion())) {
            payload.put("smartSuggestion", result.getSmartSuggestion());
        }
        return Collections.unmodifiableMap(payload);
    }

    private SanitizationOutcome<String> sanitizeText(String text, String userId) {
        if (!StringUtils.hasText(text)) {
            return SanitizationOutcome.of(text, false);
        }
        PIIDetectionResult analysis = piiDetectionService.analyze(text);
        if (!analysis.isPiiDetected()) {
            return SanitizationOutcome.of(text, false);
        }

        String sanitized = properties.isForceRedaction()
            ? redact(text, analysis.getDetections())
            : analysis.getProcessedQuery();

        boolean highRisk = analysis.getDetections().stream()
            .anyMatch(detection -> properties.isHighRiskType(detection.getType()));

        if (highRisk) {
            log.warn("High-risk PII detected in response for user={}", userId);
        } else {
            log.debug("PII detected in response for user={}, applying sanitization.", userId);
        }

        return SanitizationOutcome.of(sanitized, highRisk);
    }

    private SanitizationOutcome<Object> sanitizeObject(Object value, String userId) {
        if (value == null) {
            return SanitizationOutcome.of(Collections.emptyMap(), false);
        }

        if (value instanceof String str) {
            SanitizationOutcome<String> outcome = sanitizeText(str, userId);
            return SanitizationOutcome.of(outcome.value(), outcome.highRisk());
        }

        if (value instanceof Map<?, ?> map) {
            SanitizationOutcome<Map<String, Object>> outcome = sanitizeMap(map, userId);
            return SanitizationOutcome.of(outcome.value(), outcome.highRisk());
        }

        if (value instanceof ActionResult actionResult) {
            return sanitizeActionResult(actionResult, userId);
        }

        if (value instanceof Iterable<?> iterable) {
            return sanitizeIterable(iterable, userId);
        }

        // Preserve primitive wrapper types and other simple values
        return SanitizationOutcome.of(value, false);
    }

    @SuppressWarnings("unchecked")
    private SanitizationOutcome<Map<String, Object>> sanitizeMap(Map<?, ?> input, String userId) {
        if (CollectionUtils.isEmpty(input)) {
            return SanitizationOutcome.of(Collections.emptyMap(), false);
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        boolean highRisk = false;
        Set<String> filteredKeys = normalize(properties.getFilteredDataKeys());

        for (Map.Entry<?, ?> entry : input.entrySet()) {
            Object rawKey = entry.getKey();
            if (!(rawKey instanceof String key)) {
                continue;
            }

            if (filteredKeys.contains(key.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }

            SanitizationOutcome<Object> outcome = sanitizeObject(entry.getValue(), userId);
            if (outcome.value() != null) {
                sanitized.put(key, outcome.value());
            }
            highRisk = highRisk || outcome.highRisk();
        }

        return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), highRisk);
    }

    private SanitizationOutcome<Object> sanitizeIterable(Iterable<?> iterable, String userId) {
        List<Object> sanitized = new ArrayList<>();
        boolean highRisk = false;
        for (Object element : iterable) {
            SanitizationOutcome<Object> outcome = sanitizeObject(element, userId);
            sanitized.add(outcome.value());
            highRisk = highRisk || outcome.highRisk();
        }
        return SanitizationOutcome.of(Collections.unmodifiableList(sanitized), highRisk);
    }

    private SanitizationOutcome<Object> sanitizeActionResult(ActionResult actionResult, String userId) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        sanitized.put("success", actionResult.isSuccess());

        SanitizationOutcome<String> messageOutcome = sanitizeText(actionResult.getMessage(), userId);
        sanitized.put("message", messageOutcome.value());

        Object rawData = actionResult.getData();
        if (rawData != null) {
            SanitizationOutcome<Object> dataOutcome = sanitizeObject(rawData, userId);
            if (dataOutcome.value() != null) {
                sanitized.put("data", dataOutcome.value());
            }
            if (dataOutcome.highRisk()) {
                messageOutcome = SanitizationOutcome.of(messageOutcome.value(), true);
            }
        }

        if (properties.isIncludeErrorCodes() && StringUtils.hasText(actionResult.getErrorCode())) {
            sanitized.put("errorCode", actionResult.getErrorCode());
        }

        return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), messageOutcome.highRisk());
    }

    private SanitizationOutcome<List<Map<String, Object>>> sanitizeSuggestions(OrchestrationResult result, String userId) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        boolean highRisk = false;

        if (!CollectionUtils.isEmpty(result.getNextSteps())) {
            for (NextStepRecommendation recommendation : result.getNextSteps()) {
                SanitizationOutcome<Map<String, Object>> outcome = sanitizeRecommendation(recommendation, userId);
                if (!outcome.value().isEmpty()) {
                    suggestions.add(outcome.value());
                }
                highRisk = highRisk || outcome.highRisk();
            }
        }

        int limit = Math.max(1, properties.getSuggestionLimit());
        if (suggestions.size() > limit) {
            suggestions = new ArrayList<>(suggestions.subList(0, limit));
        }

        return SanitizationOutcome.of(Collections.unmodifiableList(suggestions), highRisk);
    }

    private SanitizationOutcome<Map<String, Object>> sanitizeRecommendation(NextStepRecommendation recommendation, String userId) {
        if (recommendation == null) {
            return SanitizationOutcome.of(Collections.emptyMap(), false);
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        boolean highRisk = false;

        if (StringUtils.hasText(recommendation.getIntent())) {
            sanitized.put("intent", recommendation.getIntent());
        }

        SanitizationOutcome<String> queryOutcome = sanitizeText(recommendation.getQuery(), userId);
        if (StringUtils.hasText(queryOutcome.value())) {
            sanitized.put("query", queryOutcome.value());
        }
        highRisk = highRisk || queryOutcome.highRisk();

        SanitizationOutcome<String> rationaleOutcome = sanitizeText(recommendation.getRationale(), userId);
        if (StringUtils.hasText(rationaleOutcome.value())) {
            sanitized.put("rationale", rationaleOutcome.value());
        }
        highRisk = highRisk || rationaleOutcome.highRisk();

        if (recommendation.getConfidence() != null) {
            sanitized.put("confidence", recommendation.getConfidence());
        }

        return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), highRisk);
    }

    private Object normalizeData(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value == null) {
            return Collections.emptyMap();
        }
        return value;
    }

    private String redact(String original, List<PIIDetection> detections) {
        if (CollectionUtils.isEmpty(detections)) {
            return original;
        }

        StringBuilder builder = new StringBuilder(original);
        detections.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(PIIDetection::getStartIndex).reversed())
            .forEach(detection -> {
                int start = Math.max(0, Math.min(detection.getStartIndex(), builder.length()));
                int end = Math.max(start, Math.min(detection.getEndIndex(), builder.length()));
                String replacement = StringUtils.hasText(detection.getMaskedValue())
                    ? detection.getMaskedValue()
                    : buildReplacementToken(detection.getType());
                builder.replace(start, end, replacement);
            });

        return builder.toString();
    }

    private String buildReplacementToken(String type) {
        if (!StringUtils.hasText(type)) {
            return properties.getDefaultReplacement();
        }
        return "[" + properties.getDefaultReplacement().replace("[", "").replace("]", "") + "_" +
            type.trim().toUpperCase(Locale.ROOT) + "]";
    }

    private Set<String> normalize(List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptySet();
        }
        return keys.stream()
            .filter(Objects::nonNull)
            .map(key -> key.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record SanitizationOutcome<T>(T value, boolean highRisk) {
        static <T> SanitizationOutcome<T> of(T value, boolean highRisk) {
            return new SanitizationOutcome<>(value, highRisk);
        }
    }
}
