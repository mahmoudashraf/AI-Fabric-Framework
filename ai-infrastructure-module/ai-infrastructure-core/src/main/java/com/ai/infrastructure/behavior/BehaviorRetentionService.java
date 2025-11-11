package com.ai.infrastructure.behavior;

import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.behavior.policy.BehaviorRetentionPolicyProvider;
import com.ai.infrastructure.dto.BehaviorRetentionResult;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.BehaviorRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled retention service responsible for purging historical behaviour data
 * according to customer supplied {@link BehaviorRetentionPolicyProvider} hooks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorRetentionService {

    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final BehaviorRepository behaviorRepository;
    private final BehaviorRetentionPolicyProvider retentionPolicyProvider;
    private final AuditService auditService;
    private final Clock clock;

    private final Map<Behavior.BehaviorType, CachedRetention> typeRetentionCache = new ConcurrentHashMap<>();
    private final Map<String, CachedRetention> userRetentionCache = new ConcurrentHashMap<>();

    /**
     * Triggered once a day at 03:00 (UTC) to remove expired behaviours.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void runScheduledCleanup() {
        BehaviorRetentionResult result = cleanupExpiredBehaviors();
        log.info("Behavior retention run completed: {}", result);
    }

    /**
     * Execute retention cleanup immediately.
     *
     * @return summary result
     */
    public BehaviorRetentionResult cleanupExpiredBehaviors() {
        BehaviorRetentionPolicyProvider provider = requireProvider();
        LocalDateTime now = LocalDateTime.now(clock);
        List<Behavior> candidates = behaviorRepository.findByCreatedAtBefore(now);

        if (candidates.isEmpty()) {
            return BehaviorRetentionResult.builder()
                .timestamp(now)
                .evaluatedCount(0)
                .deletedCount(0)
                .skippedIndefiniteCount(0)
                .build();
        }

        List<Behavior> toDelete = new ArrayList<>();
        int skippedIndefinite = 0;

        for (Behavior behavior : candidates) {
            int retentionDays = resolveRetentionDays(provider, behavior, now);
            if (retentionDays < 0) {
                skippedIndefinite++;
                continue;
            }
            LocalDateTime createdAt = behavior.getCreatedAt();
            if (createdAt == null) {
                toDelete.add(behavior);
                continue;
            }
            if (createdAt.isBefore(now.minusDays(retentionDays))) {
                toDelete.add(behavior);
            }
        }

        if (!toDelete.isEmpty()) {
            try {
                provider.beforeBehaviorDeletion(Collections.unmodifiableList(toDelete));
            } catch (Exception ex) {
                log.warn("BehaviorRetentionPolicyProvider.beforeBehaviorDeletion threw: {}", ex.getMessage());
            }
            behaviorRepository.deleteAllInBatch(toDelete);
        }

        BehaviorRetentionResult result = BehaviorRetentionResult.builder()
            .timestamp(now)
            .evaluatedCount(candidates.size())
            .deletedCount(toDelete.size())
            .skippedIndefiniteCount(skippedIndefinite)
            .build();

        auditService.logOperation(
            "behavior-retention-" + now.toLocalDate(),
            null,
            "BEHAVIOR_RETENTION",
            List.of(
                "evaluated=" + candidates.size(),
                "deleted=" + toDelete.size(),
                "skipped=" + skippedIndefinite
            ),
            now
        );

        return result;
    }

    /**
     * Clear cached retention lookups (useful when policies change at runtime).
     */
    public void clearCachedPolicies() {
        typeRetentionCache.clear();
        userRetentionCache.clear();
    }

    private BehaviorRetentionPolicyProvider requireProvider() {
        if (retentionPolicyProvider == null) {
            throw new IllegalStateException("""
                No BehaviorRetentionPolicyProvider bean available. Register an implementation of \
                com.ai.infrastructure.behavior.policy.BehaviorRetentionPolicyProvider to enable retention cleanup.""");
        }
        return retentionPolicyProvider;
    }

    private int resolveRetentionDays(BehaviorRetentionPolicyProvider provider, Behavior behavior, LocalDateTime now) {
        Behavior.BehaviorType behaviorType = Objects.requireNonNullElse(behavior.getBehaviorType(), Behavior.BehaviorType.SYSTEM_EVENT);

        int userSpecific = resolveUserRetention(provider, behaviorType, behavior.getUserId(), now);
        if (userSpecific >= 0) {
            return userSpecific;
        }

        return resolveTypeRetention(provider, behaviorType, now);
    }

    private int resolveTypeRetention(BehaviorRetentionPolicyProvider provider,
                                     Behavior.BehaviorType behaviorType,
                                     LocalDateTime now) {
        CachedRetention cached = typeRetentionCache.get(behaviorType);
        if (cached == null || cached.isExpired(now)) {
            int days = provider.getRetentionDays(behaviorType);
            cached = new CachedRetention(days, now);
            typeRetentionCache.put(behaviorType, cached);
        }
        return cached.days();
    }

    private int resolveUserRetention(BehaviorRetentionPolicyProvider provider,
                                     Behavior.BehaviorType behaviorType,
                                     UUID userId,
                                     LocalDateTime now) {
        if (userId == null) {
            return -1;
        }
        String cacheKey = behaviorType.name() + ":" + userId;
        CachedRetention cached = userRetentionCache.get(cacheKey);
        if (cached == null || cached.isExpired(now)) {
            int days = provider.getRetentionDaysForUser(behaviorType, userId.toString());
            cached = new CachedRetention(days, now);
            userRetentionCache.put(cacheKey, cached);
        }
        return cached.days();
    }

    private record CachedRetention(int days, LocalDateTime fetchedAt) {
        boolean isExpired(LocalDateTime now) {
            return fetchedAt.plus(CACHE_TTL).isBefore(now);
        }
    }
}
