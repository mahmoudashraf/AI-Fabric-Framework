package com.easyluxury.repository;

import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.service.BehaviorService;
import com.easyluxury.entity.UserBehavior;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class BehaviorBackedUserBehaviorRepository implements UserBehaviorRepository {

    private final BehaviorService behaviorService;

    @Override
    public List<UserBehavior> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return mapResponses(behaviorService.getBehaviorsByUserId(userId));
    }

    @Override
    public List<UserBehavior> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime createdAfter) {
        return mapResponses(behaviorService.getBehaviorsByUserIdAndDateRange(userId, createdAfter, LocalDateTime.now()));
    }

    @Override
    public List<UserBehavior> findByUserIdAndBehaviorTypeOrderByCreatedAtDesc(UUID userId, UserBehavior.BehaviorType behaviorType) {
        return mapResponses(behaviorService.getBehaviorsByUserId(userId)).stream()
            .filter(behavior -> behaviorTypeMatch(behavior.getBehaviorType(), behaviorType))
            .collect(Collectors.toList());
    }

    @Override
    public List<UserBehavior> findByUserIdAndEntityTypeOrderByCreatedAtDesc(UUID userId, String entityType) {
        return mapResponses(behaviorService.getBehaviorsByUserId(userId)).stream()
            .filter(behavior -> entityTypeEquals(behavior.getEntityType(), entityType))
            .collect(Collectors.toList());
    }

    @Override
    public long countByUserIdAndBehaviorType(UUID userId, UserBehavior.BehaviorType behaviorType) {
        return findByUserIdAndBehaviorTypeOrderByCreatedAtDesc(userId, behaviorType).size();
    }

    @Override
    public List<UserBehavior> findRecentBehaviorsByUser(UUID userId, LocalDateTime since) {
        return mapResponses(behaviorService.getBehaviorsByUserIdAndDateRange(userId, since, LocalDateTime.now()));
    }

    @Override
    public List<UserBehavior> findByBehaviorTypeAndDateRange(UserBehavior.BehaviorType behaviorType,
                                                             LocalDateTime startDate,
                                                             LocalDateTime endDate) {
        return mapResponses(behaviorService.getBehaviorsByDateRange(startDate, endDate)).stream()
            .filter(behavior -> behaviorTypeMatch(behavior.getBehaviorType(), behaviorType))
            .collect(Collectors.toList());
    }

    @Override
    public List<Object[]> findTopEntityTypesByUser(UUID userId) {
        return findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(behavior -> StringUtils.hasText(behavior.getEntityType()))
            .collect(Collectors.groupingBy(UserBehavior::getEntityType, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(entry -> new Object[]{entry.getKey(), entry.getValue()})
            .collect(Collectors.toList());
    }

    @Override
    public List<Object[]> findBehaviorStatisticsByUser(UUID userId) {
        return findByUserIdOrderByCreatedAtDesc(userId).stream()
            .collect(Collectors.groupingBy(UserBehavior::getBehaviorType,
                Collectors.mapping(UserBehavior::getBehaviorScore, Collectors.toList())))
            .entrySet().stream()
            .map(entry -> new Object[]{
                entry.getKey(),
                (long) entry.getValue().size(),
                average(entry.getValue())
            })
            .collect(Collectors.toList());
    }

    private List<UserBehavior> mapResponses(List<BehaviorResponse> responses) {
        return responses.stream()
            .map(this::toUserBehavior)
            .sorted(Comparator.comparing(UserBehavior::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .collect(Collectors.toList());
    }

    private UserBehavior toUserBehavior(BehaviorResponse response) {
        return UserBehavior.builder()
            .id(parseUuid(response.getId()))
            .userId(parseUuid(response.getUserId()))
            .behaviorType(parseBehaviorType(response.getBehaviorType()))
            .entityType(response.getEntityType())
            .entityId(response.getEntityId())
            .action(response.getAction())
            .context(response.getContext())
            .metadata(response.getMetadata())
            .sessionId(response.getSessionId())
            .deviceInfo(response.getDeviceInfo())
            .locationInfo(response.getLocationInfo())
            .durationSeconds(response.getDurationSeconds())
            .value(response.getValue())
            .aiAnalysis(response.getAiAnalysis())
            .aiInsights(response.getAiInsights())
            .behaviorScore(response.getBehaviorScore())
            .significanceScore(response.getSignificanceScore())
            .patternFlags(response.getPatternFlags())
            .createdAt(response.getCreatedAt())
            .build();
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UserBehavior.BehaviorType parseBehaviorType(String behaviorType) {
        if (!StringUtils.hasText(behaviorType)) {
            return UserBehavior.BehaviorType.UNKNOWN;
        }
        try {
            return UserBehavior.BehaviorType.valueOf(behaviorType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UserBehavior.BehaviorType.UNKNOWN;
        }
    }

    private boolean behaviorTypeMatch(UserBehavior.BehaviorType actual, UserBehavior.BehaviorType expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual == expected;
    }

    private boolean entityTypeEquals(String actual, String expected) {
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected)) {
            return false;
        }
        return actual.equalsIgnoreCase(expected);
    }

    private Double average(List<Double> values) {
        List<Double> numeric = values.stream()
            .filter(value -> value != null && !value.isNaN())
            .toList();
        if (numeric.isEmpty()) {
            return 0.0;
        }
        return numeric.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
}
