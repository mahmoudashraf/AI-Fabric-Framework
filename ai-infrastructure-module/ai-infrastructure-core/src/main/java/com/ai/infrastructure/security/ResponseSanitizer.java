package com.ai.infrastructure.security;

import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.PIIDetection;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
public class ResponseSanitizer {

    private final PIIDetectionService piiDetectionService;
    private final ResponseSanitizationProperties properties;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public ResponseSanitizer(PIIDetectionService piiDetectionService,
                             ResponseSanitizationProperties properties) {
        this.piiDetectionService = piiDetectionService;
        this.properties = properties;
    }

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

        RiskLevel aggregatedRisk = RiskLevel.max(
            messageOutcome.riskLevel(),
            dataOutcome.riskLevel(),
            suggestionOutcome.riskLevel(),
            smartSuggestionOutcome.riskLevel()
        );

        List<String> aggregatedTypes = mergeTypes(
            messageOutcome.detectedTypes(),
            dataOutcome.detectedTypes(),
            suggestionOutcome.detectedTypes(),
            smartSuggestionOutcome.detectedTypes()
        );

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
        payload.put("safeSummary", buildSafeSummary(messageOutcome, result));
        payload.put("sanitization", Map.of(
            "risk", aggregatedRisk.name(),
            "detectedTypes", aggregatedTypes
        ));

        if (aggregatedRisk != RiskLevel.NONE && properties.isWarningEnabled()) {
            payload.put("warning", Map.of(
                "level", aggregatedRisk == RiskLevel.HIGH
                    ? properties.getWarningLevelHighRisk()
                    : properties.getWarningLevelMediumRisk(),
                "message", aggregatedRisk == RiskLevel.HIGH
                    ? properties.getHighRiskWarningMessage()
                    : properties.getMediumRiskWarningMessage()
            ));
        }

        if (aggregatedRisk != RiskLevel.NONE && properties.isGuidanceEnabled()) {
            payload.put("guidance", properties.getGuidanceMessage());
        }

        publishSanitizationEvent(userId, aggregatedRisk, aggregatedTypes);

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
            return SanitizationOutcome.of(text, RiskLevel.NONE, List.of());
        }
        PIIDetectionResult analysis = piiDetectionService.analyze(text);
        if (!analysis.isPiiDetected()) {
            return SanitizationOutcome.of(text, RiskLevel.NONE, List.of());
        }

        String sanitized = properties.isForceRedaction()
            ? redact(text, analysis.getDetections())
            : analysis.getProcessedQuery();

        List<String> types = analysis.getDetections().stream()
            .map(PIIDetection::getType)
            .filter(StringUtils::hasText)
            .map(type -> type.trim().toUpperCase(Locale.ROOT))
            .toList();

        RiskLevel riskLevel = analysis.getDetections().stream()
            .anyMatch(detection -> properties.isHighRiskType(detection.getType()))
            ? RiskLevel.HIGH
            : RiskLevel.MEDIUM;

        if (riskLevel == RiskLevel.HIGH) {
            log.warn("High-risk PII detected in response for user={}", userId);
        } else {
            log.debug("PII detected in response for user={}, applying sanitization.", userId);
        }

        return SanitizationOutcome.of(sanitized, riskLevel, types);
    }

    private SanitizationOutcome<Object> sanitizeObject(Object value, String userId) {
        if (value == null) {
            return SanitizationOutcome.of(Collections.emptyMap(), RiskLevel.NONE, List.of());
        }

        if (value instanceof String str) {
            SanitizationOutcome<String> outcome = sanitizeText(str, userId);
            return SanitizationOutcome.of(outcome.value(), outcome.riskLevel(), outcome.detectedTypes());
        }

        if (value instanceof Map<?, ?> map) {
            SanitizationOutcome<Map<String, Object>> outcome = sanitizeMap(map, userId);
            return SanitizationOutcome.of(outcome.value(), outcome.riskLevel(), outcome.detectedTypes());
        }

        if (value instanceof ActionResult actionResult) {
            return sanitizeActionResult(actionResult, userId);
        }

        if (value instanceof Iterable<?> iterable) {
            return sanitizeIterable(iterable, userId);
        }

        return SanitizationOutcome.of(value, RiskLevel.NONE, List.of());
    }

    @SuppressWarnings("unchecked")
    private SanitizationOutcome<Map<String, Object>> sanitizeMap(Map<?, ?> input, String userId) {
        if (CollectionUtils.isEmpty(input)) {
            return SanitizationOutcome.of(Collections.emptyMap(), RiskLevel.NONE, List.of());
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        RiskLevel riskLevel = RiskLevel.NONE;
        List<String> types = new ArrayList<>();
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
            riskLevel = RiskLevel.max(riskLevel, outcome.riskLevel());
            types.addAll(outcome.detectedTypes());
        }

        return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), riskLevel, distinct(types));
    }

    private SanitizationOutcome<Object> sanitizeIterable(Iterable<?> iterable, String userId) {
        List<Object> sanitized = new ArrayList<>();
        RiskLevel riskLevel = RiskLevel.NONE;
        List<String> types = new ArrayList<>();
        for (Object element : iterable) {
            SanitizationOutcome<Object> outcome = sanitizeObject(element, userId);
            sanitized.add(outcome.value());
            riskLevel = RiskLevel.max(riskLevel, outcome.riskLevel());
            types.addAll(outcome.detectedTypes());
        }
        return SanitizationOutcome.of(Collections.unmodifiableList(sanitized), riskLevel, distinct(types));
    }

    private SanitizationOutcome<Object> sanitizeActionResult(ActionResult actionResult, String userId) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        sanitized.put("success", actionResult.isSuccess());

        SanitizationOutcome<String> messageOutcome = sanitizeText(actionResult.getMessage(), userId);
        RiskLevel riskLevel = messageOutcome.riskLevel();
        List<String> types = new ArrayList<>(messageOutcome.detectedTypes());
        sanitized.put("message", messageOutcome.value());

        Object rawData = actionResult.getData();
        if (rawData != null) {
            SanitizationOutcome<Object> dataOutcome = sanitizeObject(rawData, userId);
            if (dataOutcome.value() != null) {
                sanitized.put("data", dataOutcome.value());
            }
            riskLevel = RiskLevel.max(riskLevel, dataOutcome.riskLevel());
            types.addAll(dataOutcome.detectedTypes());
        }

        if (properties.isIncludeErrorCodes() && StringUtils.hasText(actionResult.getErrorCode())) {
            sanitized.put("errorCode", actionResult.getErrorCode());
        }

        return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), riskLevel, distinct(types));
    }

    private SanitizationOutcome<List<Map<String, Object>>> sanitizeSuggestions(OrchestrationResult result, String userId) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        RiskLevel riskLevel = RiskLevel.NONE;
        List<String> types = new ArrayList<>();

        if (!CollectionUtils.isEmpty(result.getNextSteps())) {
            for (NextStepRecommendation recommendation : result.getNextSteps()) {
                SanitizationOutcome<Map<String, Object>> outcome = sanitizeRecommendation(recommendation, userId);
                suggestions.add(outcome.value());
                riskLevel = RiskLevel.max(riskLevel, outcome.riskLevel());
                types.addAll(outcome.detectedTypes());
            }
        }

        int limit = Math.max(1, properties.getSuggestionLimit());
        if (suggestions.size() > limit) {
            suggestions = new ArrayList<>(suggestions.subList(0, limit));
        }

        return SanitizationOutcome.of(Collections.unmodifiableList(suggestions), riskLevel, distinct(types));
    }

    private SanitizationOutcome<Map<String, Object>> sanitizeRecommendation(NextStepRecommendation recommendation,
                                                                            String userId) {
        if (recommendation == null) {
            return SanitizationOutcome.of(Collections.emptyMap(), RiskLevel.NONE, List.of());
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        RiskLevel riskLevel = RiskLevel.NONE;
        List<String> types = new ArrayList<>();

        if (StringUtils.hasText(recommendation.getIntent())) {
            sanitized.put("intent", recommendation.getIntent());
        }

        SanitizationOutcome<String> queryOutcome = sanitizeText(recommendation.getQuery(), userId);
        if (StringUtils.hasText(queryOutcome.value())) {
            sanitized.put("query", queryOutcome.value());
        }
        riskLevel = RiskLevel.max(riskLevel, queryOutcome.riskLevel());
        types.addAll(queryOutcome.detectedTypes());

        SanitizationOutcome<String> rationaleOutcome = sanitizeText(recommendation.getRationale(), userId);
        if (StringUtils.hasText(rationaleOutcome.value())) {
            sanitized.put("rationale", rationaleOutcome.value());
        }
        riskLevel = RiskLevel.max(riskLevel, rationaleOutcome.riskLevel());
        types.addAll(rationaleOutcome.detectedTypes());

        if (recommendation.getConfidence() != null) {
            sanitized.put("confidence", recommendation.getConfidence());
        }

        if (properties.isIncludeSuggestionMetadata()) {
            sanitized.put("sanitization", Map.of(
                "risk", riskLevel.name(),
                "detectedTypes", distinct(types),
                "redacted", riskLevel != RiskLevel.NONE
            ));
        }

        return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), riskLevel, distinct(types));
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

    private String buildSafeSummary(SanitizationOutcome<String> messageOutcome, OrchestrationResult result) {
        if (StringUtils.hasText(messageOutcome.value())) {
            return messageOutcome.value();
        }
        if (result.getMessage() instanceof String original && StringUtils.hasText(original)) {
            return original;
        }
        return "Response generated successfully.";
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

    private void publishSanitizationEvent(String userId, RiskLevel riskLevel, List<String> detectedTypes) {
        if (!properties.isPublishEvents() || eventPublisher == null || riskLevel == RiskLevel.NONE) {
            return;
        }
        eventPublisher.publishEvent(new SanitizationEvent(this, userId, riskLevel, detectedTypes));
    }

    private List<String> mergeTypes(List<String>... typeLists) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (List<String> list : typeLists) {
            if (list != null) {
                list.stream()
                    .filter(StringUtils::hasText)
                    .map(type -> type.trim().toUpperCase(Locale.ROOT))
                    .forEach(merged::add);
            }
        }
        return List.copyOf(merged);
    }

    private List<String> distinct(List<String> types) {
        return mergeTypes(types);
    }

    enum RiskLevel {
        NONE,
        MEDIUM,
        HIGH;

        static RiskLevel max(RiskLevel... levels) {
            RiskLevel result = NONE;
            for (RiskLevel level : levels) {
                if (level != null && level.ordinal() > result.ordinal()) {
                    result = level;
                }
            }
            return result;
        }
    }

    private record SanitizationOutcome<T>(T value, RiskLevel riskLevel, List<String> detectedTypes) {
        static <T> SanitizationOutcome<T> of(T value, RiskLevel riskLevel, List<String> detectedTypes) {
            return new SanitizationOutcome<>(value, riskLevel, detectedTypes);
        }
    }
}
