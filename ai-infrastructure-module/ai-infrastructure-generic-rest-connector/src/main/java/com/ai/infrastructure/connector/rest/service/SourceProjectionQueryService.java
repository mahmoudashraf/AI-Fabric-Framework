package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SourceProjectionQueryService {

    private static final int MAX_FILTER_VALUES = 20;

    private final IntegrationStateRepository repository;
    private final Clock clock;

    @Autowired
    public SourceProjectionQueryService(IntegrationStateRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SourceProjectionQueryService(IntegrationStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Map<String, Object> query(
        RestRoutingConfig.SourceProjectionQuery config,
        Map<String, Object> params
    ) {
        if (config == null || !StringUtils.hasText(config.getSourceRef())) {
            throw new ProjectionQueryException("INVALID_REQUEST", "Source projection query is not configured.");
        }
        String sourceId = config.getSourceRef().trim();
        IntegrationStateRepository.SyncState state = repository.syncState(sourceId).orElse(null);
        validateFreshness(config, state);

        Map<String, Object> safeParams = params != null ? params : Map.of();
        List<IntegrationStateRepository.ProjectionCriterion> criteria = new ArrayList<>();
        Map<String, Object> appliedFilters = new LinkedHashMap<>();
        List<RestRoutingConfig.SourceProjectionFilter> filters = config.getFilters() != null
            ? config.getFilters()
            : List.of();
        for (RestRoutingConfig.SourceProjectionFilter filter : filters) {
            Object rawValue = safeParams.get(filter.getParam());
            List<String> values = normalizedValues(filter, rawValue);
            if (values.isEmpty()) {
                continue;
            }
            criteria.add(new IntegrationStateRepository.ProjectionCriterion(
                List.copyOf(filter.getFields()),
                repositoryOperator(filter.getOperator()),
                values
            ));
            appliedFilters.put(filter.getParam(), values.size() == 1 ? values.getFirst() : values);
        }

        int limit = resolveLimit(config, safeParams.get(config.getLimitParam()));
        IntegrationStateRepository.ProjectionQueryResult result = repository.queryProjection(
            sourceId,
            new IntegrationStateRepository.ProjectionQuery(List.copyOf(criteria), limit)
        );

        List<Map<String, Object>> records = result.records().stream()
            .map(record -> project(record.entity(), config.getOutputFields()))
            .toList();
        long freshnessSeconds = state != null && state.lastSuccessAt() != null
            ? Math.max(0L, Duration.between(state.lastSuccessAt(), clock.instant()).toSeconds())
            : 0L;

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceId", sourceId);
        if (state != null && StringUtils.hasText(state.sourceVersion())) {
            source.put("sourceVersion", state.sourceVersion());
        }
        if (state != null && state.lastSuccessAt() != null) {
            source.put("synchronizedAt", state.lastSuccessAt().toString());
        }
        source.put("freshnessSeconds", freshnessSeconds);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", records);
        response.put("returnedCount", records.size());
        response.put("totalResults", result.totalMatches());
        response.put("appliedFilters", Collections.unmodifiableMap(appliedFilters));
        response.put("source", Collections.unmodifiableMap(source));
        return Collections.unmodifiableMap(response);
    }

    private void validateFreshness(
        RestRoutingConfig.SourceProjectionQuery config,
        IntegrationStateRepository.SyncState state
    ) {
        if (!config.isRequireSuccessfulSync()) {
            return;
        }
        if (state == null || state.lastSuccessAt() == null) {
            throw new ProjectionQueryException(
                "SOURCE_PROJECTION_NOT_READY",
                "The synchronized source projection is not ready."
            );
        }
        Instant oldestAccepted = clock.instant().minusSeconds(config.getMaxStalenessSeconds());
        if (state.lastSuccessAt().isBefore(oldestAccepted)) {
            throw new ProjectionQueryException(
                "SOURCE_PROJECTION_STALE",
                "The synchronized source projection is stale."
            );
        }
    }

    private List<String> normalizedValues(RestRoutingConfig.SourceProjectionFilter filter, Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        String text = rawValue.toString().trim();
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        if (filter.getOperator() == RestRoutingConfig.SourceProjectionFilterOperator.NUMBER_LESS_THAN_OR_EQUAL) {
            try {
                String normalized = normalizeNumber(text);
                return isConfiguredAbsentValue(filter, normalized) ? List.of() : List.of(normalized);
            } catch (NumberFormatException exception) {
                throw new ProjectionQueryException("INVALID_REQUEST", "Numeric source projection filter is invalid.");
            }
        }
        if (filter.getOperator() != RestRoutingConfig.SourceProjectionFilterOperator.ANY_TOKEN_EQUALS_IGNORE_CASE) {
            return isConfiguredAbsentValue(filter, text) ? List.of() : List.of(text);
        }
        String delimiter = StringUtils.hasText(filter.getTokenDelimiter()) ? filter.getTokenDelimiter() : ",";
        List<String> values = Pattern.compile(Pattern.quote(delimiter)).splitAsStream(text)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .filter(value -> !isConfiguredAbsentValue(filter, value))
            .distinct()
            .limit(MAX_FILTER_VALUES + 1L)
            .toList();
        if (values.size() > MAX_FILTER_VALUES) {
            throw new ProjectionQueryException("INVALID_REQUEST", "Source projection filter contains too many values.");
        }
        return values;
    }

    private boolean isConfiguredAbsentValue(RestRoutingConfig.SourceProjectionFilter filter, String normalizedValue) {
        if (filter.getAbsentValues() == null || filter.getAbsentValues().isEmpty()) {
            return false;
        }
        return filter.getAbsentValues().stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(value -> filter.getOperator() == RestRoutingConfig.SourceProjectionFilterOperator.NUMBER_LESS_THAN_OR_EQUAL
                ? normalizeNumber(value)
                : value)
            .anyMatch(value -> value.equalsIgnoreCase(normalizedValue));
    }

    private String normalizeNumber(String value) {
        return new BigDecimal(value).stripTrailingZeros().toPlainString();
    }

    private int resolveLimit(RestRoutingConfig.SourceProjectionQuery config, Object rawLimit) {
        int limit = config.getDefaultLimit();
        if (rawLimit != null && StringUtils.hasText(rawLimit.toString())) {
            try {
                limit = Integer.parseInt(rawLimit.toString().trim());
            } catch (NumberFormatException exception) {
                throw new ProjectionQueryException("INVALID_REQUEST", "Source projection result limit is invalid.");
            }
        }
        if (limit < 1 || limit > config.getMaxLimit()) {
            throw new ProjectionQueryException(
                "INVALID_REQUEST",
                "Source projection result limit must be between 1 and " + config.getMaxLimit() + "."
            );
        }
        return limit;
    }

    private Map<String, Object> project(Map<String, Object> entity, List<String> outputFields) {
        if (entity == null || entity.isEmpty()) {
            return Map.of();
        }
        if (outputFields == null || outputFields.isEmpty()) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(entity));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String field : outputFields) {
            if (entity.containsKey(field)) {
                out.put(field, entity.get(field));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private IntegrationStateRepository.ProjectionOperator repositoryOperator(
        RestRoutingConfig.SourceProjectionFilterOperator operator
    ) {
        return switch (operator) {
            case EQUALS_IGNORE_CASE -> IntegrationStateRepository.ProjectionOperator.EQUALS_IGNORE_CASE;
            case NUMBER_LESS_THAN_OR_EQUAL -> IntegrationStateRepository.ProjectionOperator.NUMBER_LESS_THAN_OR_EQUAL;
            case ANY_TOKEN_EQUALS_IGNORE_CASE -> IntegrationStateRepository.ProjectionOperator.ANY_TOKEN_EQUALS_IGNORE_CASE;
        };
    }

    public static final class ProjectionQueryException extends RuntimeException {
        private final String errorCode;

        public ProjectionQueryException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
