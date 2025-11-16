package com.ai.behavior.model;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent query object for retrieving behavior events via repositories.
 */
@Value
@Builder(toBuilder = true)
public class BehaviorQuery {

    UUID userId;
    String sessionId;
    String entityType;
    String entityId;
    EventType eventType;
    List<EventType> eventTypes;
    LocalDateTime startTime;
    LocalDateTime endTime;
    Integer limit;
    Integer offset;
    @Builder.Default
    boolean orderDesc = true;
    Map<String, Object> metadataContains;

    public static BehaviorQuery forUser(UUID userId) {
        return BehaviorQuery.builder().userId(userId).orderDesc(true).build();
    }

    public static BehaviorQuery forEntity(String entityType, String entityId) {
        return BehaviorQuery.builder()
            .entityType(entityType)
            .entityId(entityId)
            .orderDesc(true)
            .build();
    }

    public BehaviorQuery limit(int newLimit) {
        return toBuilder().limit(newLimit).build();
    }

    public BehaviorQuery range(LocalDateTime start, LocalDateTime end) {
        return toBuilder().startTime(start).endTime(end).build();
    }

    public Specification<BehaviorEvent> toSpecification() {
        return this::buildPredicate;
    }

    private Predicate buildPredicate(Root<BehaviorEvent> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (userId != null) {
            predicates.add(cb.equal(root.get("userId"), userId));
        }
        if (sessionId != null) {
            predicates.add(cb.equal(root.get("sessionId"), sessionId));
        }
        if (entityType != null) {
            predicates.add(cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null) {
            predicates.add(cb.equal(root.get("entityId"), entityId));
        }
        if (eventType != null) {
            predicates.add(cb.equal(root.get("eventType"), eventType));
        } else if (eventTypes != null && !eventTypes.isEmpty()) {
            predicates.add(root.get("eventType").in(eventTypes));
        }
        if (startTime != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startTime));
        }
        if (endTime != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endTime));
        }

        if (metadataContains != null && !metadataContains.isEmpty()) {
            metadataContains.forEach((key, value) -> {
                if (key == null || value == null) {
                    return;
                }
                predicates.add(cb.equal(
                    cb.function(
                        "jsonb_extract_path_text",
                        String.class,
                        root.get("metadata"),
                        cb.literal(key)
                    ),
                    value.toString()
                ));
            });
        }

        if (orderDesc) {
            query.orderBy(cb.desc(root.get("timestamp")));
        } else {
            query.orderBy(cb.asc(root.get("timestamp")));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
