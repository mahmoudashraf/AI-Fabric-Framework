package com.easyluxury.repository;

import com.easyluxury.entity.UserBehavior;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface UserBehaviorRepository {

    List<UserBehavior> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<UserBehavior> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime createdAfter);

    List<UserBehavior> findByUserIdAndBehaviorTypeOrderByCreatedAtDesc(UUID userId, UserBehavior.BehaviorType behaviorType);

    List<UserBehavior> findByUserIdAndEntityTypeOrderByCreatedAtDesc(UUID userId, String entityType);

    long countByUserIdAndBehaviorType(UUID userId, UserBehavior.BehaviorType behaviorType);

    List<UserBehavior> findRecentBehaviorsByUser(UUID userId, LocalDateTime since);

    List<UserBehavior> findByBehaviorTypeAndDateRange(UserBehavior.BehaviorType behaviorType,
                                                      LocalDateTime startDate,
                                                      LocalDateTime endDate);

    List<Object[]> findTopEntityTypesByUser(UUID userId);

    List<Object[]> findBehaviorStatisticsByUser(UUID userId);
}
