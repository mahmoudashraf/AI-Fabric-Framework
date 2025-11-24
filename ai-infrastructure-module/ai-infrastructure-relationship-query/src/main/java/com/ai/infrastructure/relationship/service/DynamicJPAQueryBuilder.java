package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.dto.JpqlQuery;
import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.model.EntityMapping;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Converts {@link RelationshipQueryPlan} objects into executable JPQL queries.
 */
public class DynamicJPAQueryBuilder {

    private final EntityRelationshipMapper relationshipMapper;
    public DynamicJPAQueryBuilder(EntityRelationshipMapper relationshipMapper) {
        this.relationshipMapper = Objects.requireNonNull(relationshipMapper);
    }

    public JpqlQuery buildQuery(RelationshipQueryPlan plan) {
        EntityMapping rootMapping = relationshipMapper.getEntityMapping(plan.getPrimaryEntityType());
        String rootAlias = "root";
        AliasRegistry aliases = new AliasRegistry();
        aliases.register(plan.getPrimaryEntityType(), rootAlias);

        StringBuilder builder = new StringBuilder()
            .append("SELECT DISTINCT ").append(rootAlias)
            .append(" FROM ").append(rootMapping.simpleClassName()).append(" ").append(rootAlias);

        List<String> joins = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        Map<String, Object> parameters = new LinkedHashMap<>();
        AtomicInteger paramSequence = new AtomicInteger(0);

        appendRelationshipJoins(plan.getRelationshipPaths(), aliases, joins, predicates, parameters, paramSequence);
        appendFilters(plan, aliases, predicates, parameters, paramSequence);

        joins.forEach(builder::append);
        if (!predicates.isEmpty()) {
            builder.append(" WHERE ")
                .append(String.join(" AND ", predicates));
        }

        return JpqlQuery.builder()
            .jpql(builder.toString())
            .parameters(parameters)
            .limit(plan.getLimit())
            .rootAlias(rootAlias)
            .build();
    }

    private void appendRelationshipJoins(List<RelationshipPath> paths,
                                         AliasRegistry aliases,
                                         List<String> joins,
                                         List<String> predicates,
                                         Map<String, Object> parameters,
                                         AtomicInteger paramSequence) {
        if (CollectionUtils.isEmpty(paths)) {
            return;
        }
        preRegisterAliases(paths, aliases);
        for (RelationshipPath path : paths) {
            if (path == null || !StringUtils.hasText(path.getRelationshipType())) {
                continue;
            }
            String fromAlias = aliases.aliasFor(path.getFromEntityType());
            if (!StringUtils.hasText(fromAlias)) {
                fromAlias = aliases.register(path.getFromEntityType());
            }
            String toAlias = aliases.aliasFor(path.getToEntityType());
            if (!StringUtils.hasText(toAlias)) {
                toAlias = aliases.register(path.getToEntityType());
            }
            String joinKeyword = path.isOptional() ? " LEFT JOIN " : " JOIN ";
            joins.add(joinKeyword + fromAlias + "." + path.getRelationshipType() + " " + toAlias);
            if (!CollectionUtils.isEmpty(path.getConditions())) {
                String targetAlias = toAlias;
                path.getConditions().forEach(condition ->
                    predicates.add(buildPredicate(condition, targetAlias, aliases, parameters, paramSequence)));
            }
        }
    }

    private void preRegisterAliases(List<RelationshipPath> paths, AliasRegistry aliases) {
        for (RelationshipPath path : paths) {
            if (path == null) {
                continue;
            }
            if (StringUtils.hasText(path.getFromEntityType()) && aliases.aliasFor(path.getFromEntityType()) == null) {
                aliases.register(path.getFromEntityType());
            }
            if (!StringUtils.hasText(path.getToEntityType())) {
                continue;
            }
            if (StringUtils.hasText(path.getAlias())) {
                aliases.register(path.getToEntityType(), path.getAlias());
            } else if (aliases.aliasFor(path.getToEntityType()) == null) {
                aliases.register(path.getToEntityType());
            }
        }
    }

    private void appendFilters(RelationshipQueryPlan plan,
                               AliasRegistry aliases,
                               List<String> predicates,
                               Map<String, Object> parameters,
                               AtomicInteger paramSequence) {
        if (!CollectionUtils.isEmpty(plan.getDirectFilters())) {
            plan.getDirectFilters().forEach((entity, filters) ->
                appendFilterGroup(entity, filters, aliases, predicates, parameters, paramSequence));
        }
        if (!CollectionUtils.isEmpty(plan.getRelationshipFilters())) {
            plan.getRelationshipFilters().forEach((entity, filters) ->
                appendFilterGroup(entity, filters, aliases, predicates, parameters, paramSequence));
        }
    }

    private void appendFilterGroup(String entity,
                                   List<FilterCondition> filters,
                                   AliasRegistry aliases,
                                   List<String> predicates,
                                   Map<String, Object> parameters,
                                   AtomicInteger paramSequence) {
        if (CollectionUtils.isEmpty(filters)) {
            return;
        }
        String alias = aliases.aliasFor(entity);
        if (alias == null) {
            alias = aliases.register(entity);
        }
        for (FilterCondition condition : filters) {
            predicates.add(buildPredicate(condition, alias, aliases, parameters, paramSequence));
        }
    }

    private String buildPredicate(FilterCondition condition,
                                  String alias,
                                  AliasRegistry aliases,
                                  Map<String, Object> parameters,
                                  AtomicInteger paramSequence) {
        String fieldName = resolveFieldName(condition, alias);
        String parameterName = "p" + paramSequence.incrementAndGet();
        Object value = condition.getValue();
        FilterOperator operator = condition.getOperator() != null
            ? condition.getOperator()
            : FilterOperator.EQUALS;
        String referencedField = resolveFieldReference(value, aliases);
        if (referencedField == null) {
            value = coerceParameterValue(value);
        }

        return switch (operator) {
            case EQUALS -> {
                if (referencedField != null) {
                    yield "%s = %s".formatted(fieldName, referencedField);
                }
                parameters.put(parameterName, value);
                yield "%s = :%s".formatted(fieldName, parameterName);
            }
            case NOT_EQUALS -> {
                if (referencedField != null) {
                    yield "%s <> %s".formatted(fieldName, referencedField);
                }
                parameters.put(parameterName, value);
                yield "%s <> :%s".formatted(fieldName, parameterName);
            }
            case GREATER_THAN -> {
                parameters.put(parameterName, value);
                yield "%s > :%s".formatted(fieldName, parameterName);
            }
            case GREATER_THAN_OR_EQUAL -> {
                parameters.put(parameterName, value);
                yield "%s >= :%s".formatted(fieldName, parameterName);
            }
            case LESS_THAN -> {
                parameters.put(parameterName, value);
                yield "%s < :%s".formatted(fieldName, parameterName);
            }
            case LESS_THAN_OR_EQUAL -> {
                parameters.put(parameterName, value);
                yield "%s <= :%s".formatted(fieldName, parameterName);
            }
            case LIKE -> {
                parameters.put(parameterName, value);
                yield "%s LIKE :%s".formatted(fieldName, parameterName);
            }
            case ILIKE -> {
                parameters.put(parameterName, value != null ? value.toString().toLowerCase(Locale.ROOT) : null);
                yield "LOWER(%s) LIKE :%s".formatted(fieldName, parameterName);
            }
            case IN, NOT_IN -> buildCollectionPredicate(operator, fieldName, parameterName, value, parameters);
            case BETWEEN -> buildBetweenPredicate(fieldName, parameterName, condition, parameters, paramSequence);
            case EXISTS -> "%s IS NOT NULL".formatted(fieldName);
            case NOT_EXISTS -> "%s IS NULL".formatted(fieldName);
        };
    }

    private String buildCollectionPredicate(FilterOperator operator,
                                            String field,
                                            String parameterName,
                                            Object value,
                                            Map<String, Object> parameters) {
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(item -> values.add(coerceParameterValue(item)));
            parameters.put(parameterName, values);
        } else if (value != null && value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            List<Object> values = new ArrayList<>(array.length);
            for (Object element : array) {
                values.add(coerceParameterValue(element));
            }
            parameters.put(parameterName, values);
        } else {
            parameters.put(parameterName, List.of(coerceParameterValue(value)));
        }
        String clause = "%s %s :%s".formatted(field, operator == FilterOperator.IN ? "IN" : "NOT IN", parameterName);
        return clause;
    }

    private String buildBetweenPredicate(String field,
                                         String parameterName,
                                         FilterCondition condition,
                                         Map<String, Object> parameters,
                                         AtomicInteger paramSequence) {
        String secondParameter = "p" + paramSequence.incrementAndGet();
        parameters.put(parameterName, coerceParameterValue(condition.getValue()));
        parameters.put(secondParameter, coerceParameterValue(condition.getSecondaryValue()));
        return "%s BETWEEN :%s AND :%s".formatted(field, parameterName, secondParameter);
    }

    private String resolveFieldName(FilterCondition condition, String defaultAlias) {
        if (!StringUtils.hasText(condition.getField())) {
            return defaultAlias;
        }
        if (condition.getField().contains(".")) {
            return condition.getField();
        }
        return defaultAlias + "." + condition.getField();
    }

    private String resolveFieldReference(Object value, AliasRegistry aliases) {
        if (!(value instanceof String str)) {
            return null;
        }
        String trimmed = str.trim();
        if (!trimmed.contains(".")) {
            return null;
        }
        String[] parts = trimmed.split("\\.", 2);
        if (parts.length != 2) {
            return null;
        }
        String refAlias = aliases.aliasFor(parts[0]);
        if (!StringUtils.hasText(refAlias)) {
            return null;
        }
        return refAlias + "." + parts[1];
    }

    private Object coerceParameterValue(Object value) {
        if (!(value instanceof String str)) {
            return value;
        }
        String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            return value;
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        return value;
    }

    private static class AliasRegistry {
        private final Map<String, String> aliasByEntity = new LinkedHashMap<>();
        private final Map<String, Integer> aliasCounters = new LinkedHashMap<>();

        String register(String entityType) {
            return aliasByEntity.computeIfAbsent(normalize(entityType), key -> {
                int index = aliasCounters.merge(key, 1, Integer::sum);
                return (key.length() > 2 ? key.substring(0, 2) : key) + index;
            });
        }

        String register(String entityType, String alias) {
            aliasByEntity.put(normalize(entityType), alias);
            return alias;
        }

        String aliasFor(String entityType) {
            return aliasByEntity.get(normalize(entityType));
        }

        private String normalize(String entityType) {
            if (!StringUtils.hasText(entityType)) {
                return "entity";
            }
            return entityType.trim().toLowerCase(Locale.ROOT);
        }
    }
}
