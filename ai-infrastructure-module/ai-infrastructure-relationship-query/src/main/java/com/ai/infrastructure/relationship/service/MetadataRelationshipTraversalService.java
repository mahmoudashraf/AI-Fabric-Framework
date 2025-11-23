package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.JpqlQuery;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Fallback traversal strategy that inspects indexed metadata when direct JPA joins are insufficient.
 */
public class MetadataRelationshipTraversalService implements RelationshipTraversalService {

    private static final TypeReference<Map<String, Object>> MAP_REFERENCE = new TypeReference<>() {};

    private final AISearchableEntityRepository entityRepository;
    private final ObjectMapper objectMapper;

    public MetadataRelationshipTraversalService(AISearchableEntityRepository entityRepository,
                                                ObjectMapper objectMapper) {
        this.entityRepository = Objects.requireNonNull(entityRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public TraversalMode getMode() {
        return TraversalMode.METADATA;
    }

    @Override
    public boolean supports(RelationshipQueryPlan plan) {
        return plan != null && StringUtils.hasText(plan.getPrimaryEntityType());
    }

    @Override
    public List<String> traverse(RelationshipQueryPlan plan, JpqlQuery query) {
        if (!supports(plan)) {
            return Collections.emptyList();
        }

        List<AISearchableEntity> candidates = entityRepository.findByEntityType(plan.getPrimaryEntityType());
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<FilterCondition> filterConditions = mergeFilters(plan);
        if (filterConditions.isEmpty()) {
            return extractIds(candidates, query != null ? query.getLimit() : null);
        }

        List<String> matches = new ArrayList<>();
        for (AISearchableEntity entity : candidates) {
            if (!StringUtils.hasText(entity.getMetadata())) {
                continue;
            }
            try {
                Map<String, Object> metadata = objectMapper.readValue(entity.getMetadata(), MAP_REFERENCE);
                if (matches(metadata, filterConditions)) {
                    matches.add(entity.getEntityId());
                }
            } catch (IOException ignored) {
                // Skip malformed metadata entries
            }
            if (query != null && query.getLimit() != null && matches.size() >= query.getLimit()) {
                break;
            }
        }

        return matches;
    }

    private List<String> extractIds(List<AISearchableEntity> entities, Integer limit) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (AISearchableEntity entity : entities) {
            ids.add(entity.getEntityId());
            if (limit != null && ids.size() >= limit) {
                break;
            }
        }
        return ids;
    }

    private List<FilterCondition> mergeFilters(RelationshipQueryPlan plan) {
        List<FilterCondition> merged = new ArrayList<>();
        if (!CollectionUtils.isEmpty(plan.getDirectFilters())) {
            plan.getDirectFilters().values().stream()
                .filter(Objects::nonNull)
                .forEach(merged::addAll);
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipFilters())) {
            plan.getRelationshipFilters().values().stream()
                .filter(Objects::nonNull)
                .forEach(merged::addAll);
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipPaths())) {
            plan.getRelationshipPaths().forEach(path -> {
                if (path != null && !CollectionUtils.isEmpty(path.getConditions())) {
                    merged.addAll(path.getConditions());
                }
            });
        }
        return merged;
    }

    private boolean matches(Map<String, Object> metadata, List<FilterCondition> filters) {
        for (FilterCondition condition : filters) {
            Object value = lookup(metadata, condition.getField());
            if (!evaluateCondition(value, condition)) {
                return false;
            }
        }
        return true;
    }

    private Object lookup(Map<String, Object> metadata, String field) {
        if (!StringUtils.hasText(field) || metadata == null) {
            return null;
        }
        if (metadata.containsKey(field)) {
            return metadata.get(field);
        }
        int dot = field.lastIndexOf('.');
        if (dot >= 0) {
            String suffix = field.substring(dot + 1);
            if (metadata.containsKey(suffix)) {
                return metadata.get(suffix);
            }
            String condensed = field.replace(".", "");
            return metadata.get(condensed);
        }
        return null;
    }

    private boolean evaluateCondition(Object candidate, FilterCondition condition) {
        FilterOperator operator = condition.getOperator() != null ? condition.getOperator() : FilterOperator.EQUALS;
        Object expected = condition.getValue();
        return switch (operator) {
            case EQUALS -> compareStrings(candidate, expected) == 0;
            case NOT_EQUALS -> compareStrings(candidate, expected) != 0;
            case GREATER_THAN -> compareNumbers(candidate, expected) > 0;
            case GREATER_THAN_OR_EQUAL -> compareNumbers(candidate, expected) >= 0;
            case LESS_THAN -> compareNumbers(candidate, expected) < 0;
            case LESS_THAN_OR_EQUAL -> compareNumbers(candidate, expected) <= 0;
            case LIKE, ILIKE -> {
                String haystack = normalize(candidate);
                String needle = normalize(expected);
                yield haystack != null && needle != null && haystack.contains(needle.replace("%", ""));
            }
            case IN -> containsValue(candidate, expected, true);
            case NOT_IN -> !containsValue(candidate, expected, true);
            case BETWEEN -> {
                double first = compareNumbers(candidate, expected);
                double second = compareNumbers(candidate, condition.getSecondaryValue());
                yield first >= 0 && second <= 0;
            }
            case EXISTS -> candidate != null;
            case NOT_EXISTS -> candidate == null;
        };
    }

    private int compareStrings(Object first, Object second) {
        String left = normalize(first);
        String right = normalize(second);
        if (left == null || right == null) {
            return left == right ? 0 : -1;
        }
        return left.compareTo(right);
    }

    private int compareNumbers(Object first, Object second) {
        double a = parseDouble(first);
        double b = parseDouble(second);
        return Double.compare(a, b);
    }

    private double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean containsValue(Object candidate, Object expected, boolean normalize) {
        if (expected instanceof Iterable<?> iterable) {
            for (Object option : iterable) {
                if (normalize) {
                    if (Objects.equals(normalize(candidate), normalize(option))) {
                        return true;
                    }
                } else if (Objects.equals(candidate, option)) {
                    return true;
                }
            }
            return false;
        }
        if (expected != null && expected.getClass().isArray()) {
            Object[] array = (Object[]) expected;
            for (Object option : array) {
                if (Objects.equals(normalize(candidate), normalize(option))) {
                    return true;
                }
            }
            return false;
        }
        return Objects.equals(normalize(candidate), normalize(expected));
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString().trim().toLowerCase(Locale.ROOT);
    }
}
