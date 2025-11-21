package com.ai.behavior.service;

import com.ai.behavior.dto.BehaviorInsightView;
import com.ai.behavior.dto.OrchestratedQueryRequest;
import com.ai.behavior.dto.OrchestratedSearchResponse;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.service.BehaviorSearchService.SearchParameters;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorQueryOrchestrator {

    private static final Pattern SEGMENT_PATTERN = Pattern.compile("segment[:=](\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_PATTERN = Pattern.compile("pattern[:=]([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("confidence\\s*(?:>=|>|:)\\s*(0?(?:\\.\\d+)|1(?:\\.0)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_PATTERN = Pattern.compile("user[:=]([0-9a-fA-F-]{36})");

    private final RAGOrchestrator ragOrchestrator;
    private final PIIDetectionService piiDetectionService;
    private final BehaviorSearchService searchService;
    private final BehaviorAuditService auditService;
    private final BehaviorMetricsService metricsService;

    public OrchestratedSearchResponse executeQuery(OrchestratedQueryRequest request) {
        PIIDetectionResult piiResult = piiDetectionService.detectAndProcess(request.getQuery());
        if (piiResult.isPiiDetected()) {
            auditService.logPiiDetection(request.getQuery());
            metricsService.incrementPiiDetections();
            return OrchestratedSearchResponse.builder()
                .query(request.getQuery())
                .executedAt(Instant.now())
                .piiDetected(true)
                .error("Query blocked because it contained PII. Please remove sensitive information and try again.")
                .build();
        }

        String orchestratedQuery = StringUtils.hasText(piiResult.getProcessedQuery())
            ? piiResult.getProcessedQuery()
            : request.getQuery();

        OrchestrationResult orchestrationResult = ragOrchestrator.orchestrate(orchestratedQuery, request.getUserId());
        if (orchestrationResult == null || !orchestrationResult.isSuccess()) {
            auditService.logQueryExecuted(orchestratedQuery, false, 0);
            return OrchestratedSearchResponse.builder()
                .query(orchestratedQuery)
                .executedAt(Instant.now())
                .piiDetected(false)
                .error(orchestrationResult != null ? orchestrationResult.getMessage() : "Unable to orchestrate query.")
                .build();
        }

        SearchParameters parameters = buildSearchParameters(orchestratedQuery, orchestrationResult, request);
        List<BehaviorInsights> insights = metricsService.recordSearch(() -> searchService.search(parameters));
        auditService.logQueryExecuted(orchestratedQuery, true, insights.size());

        return OrchestratedSearchResponse.builder()
            .query(orchestratedQuery)
            .executedAt(Instant.now())
            .piiDetected(false)
            .results(OrchestratedSearchResponse.SearchResults.builder()
                .matchedUsers(insights.stream()
                    .map(BehaviorInsights::getUserId)
                    .filter(uuid -> uuid != null)
                    .distinct()
                    .toList())
                .totalMatches(insights.size())
                .searchStrategy(determineStrategy(orchestrationResult))
                .aiExplanation(request.isIncludeExplanation() ? resolveExplanation(orchestrationResult) : null)
                .insights(insights.stream().map(BehaviorInsightView::from).toList())
                .build())
            .build();
    }

    private SearchParameters buildSearchParameters(String query, OrchestrationResult orchestrationResult, OrchestratedQueryRequest request) {
        SearchParameters.Builder builder = SearchParameters.builder()
            .limit(request.getLimit());

        extractSegment(query, orchestrationResult).ifPresent(builder::segment);
        extractPattern(query).ifPresent(builder::pattern);
        extractConfidence(query).ifPresent(builder::minConfidence);
        List<UUID> userFilters = extractUserFilters(query);
        if (!CollectionUtils.isEmpty(userFilters)) {
            builder.userIds(userFilters);
        }

        return builder.build();
    }

    private java.util.Optional<String> extractSegment(String query, OrchestrationResult orchestrationResult) {
        Matcher matcher = SEGMENT_PATTERN.matcher(query);
        if (matcher.find()) {
            return java.util.Optional.ofNullable(matcher.group(1));
        }
        Object metadataSegment = orchestrationResult.getMetadata().get("suggestedSegment");
        if (metadataSegment instanceof String value && StringUtils.hasText(value)) {
            return java.util.Optional.of(value);
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> extractPattern(String query) {
        Matcher matcher = PATTERN_PATTERN.matcher(query);
        if (matcher.find()) {
            return java.util.Optional.ofNullable(matcher.group(1));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Double> extractConfidence(String query) {
        Matcher matcher = CONFIDENCE_PATTERN.matcher(query);
        if (matcher.find()) {
            String value = matcher.group(1);
            try {
                return java.util.Optional.of(Double.parseDouble(value));
            } catch (NumberFormatException ignored) {
                log.debug("Unable to parse confidence expression '{}'", value);
            }
        }
        return java.util.Optional.empty();
    }

    private List<UUID> extractUserFilters(String query) {
        Matcher matcher = USER_PATTERN.matcher(query);
        return matcher.results()
            .map(matchResult -> matchResult.group(1))
            .map(this::safeUuid)
            .filter(uuid -> uuid != null)
            .collect(Collectors.toList());
    }

    private UUID safeUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String determineStrategy(OrchestrationResult result) {
        if (result.getType() != null) {
            return result.getType().name();
        }
        if (result.getMetadata().containsKey("intentsCount")) {
            return "intent-driven";
        }
        return "semantic";
    }

    private String resolveExplanation(OrchestrationResult result) {
        if (StringUtils.hasText(result.getMessage())) {
            return result.getMessage();
        }
        Object answer = result.getData().get("answer");
        return answer != null ? answer.toString() : null;
    }
}
