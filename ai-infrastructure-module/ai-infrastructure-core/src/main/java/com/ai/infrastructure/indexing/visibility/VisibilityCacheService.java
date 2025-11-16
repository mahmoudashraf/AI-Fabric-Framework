package com.ai.infrastructure.indexing.visibility;

import com.ai.infrastructure.config.VisibilityCacheProperties;
import com.ai.infrastructure.indexing.IndexingActionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory cache that exposes near-real-time views of entities for ASYNC flows.
 */
@Slf4j
@RequiredArgsConstructor
public class VisibilityCacheService {

    private final VisibilityCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void record(String entityType, String entityId, Object entity, IndexingActionPlan plan) {
        if (!properties.isEnabled() || entityId == null) {
            return;
        }
        String key = cacheKey(entityType, entityId);
        evictExpired();

        if (plan.removeFromSearch() || plan.cleanupEmbeddings()) {
            cache.remove(key);
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(entity);
            if (cache.size() >= properties.getMaxEntries()) {
                pruneOldest();
            }
            cache.put(key, new CacheEntry(payload, Instant.now(clock)));
        } catch (Exception ex) {
            log.debug("Failed to serialize entity {}:{} for visibility cache", entityType, entityId, ex);
        }
    }

    public Optional<String> get(String entityType, String entityId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        CacheEntry entry = cache.get(cacheKey(entityType, entityId));
        if (entry == null || entry.isExpired(properties.getTtl(), clock)) {
            cache.remove(cacheKey(entityType, entityId));
            return Optional.empty();
        }
        return Optional.of(entry.payload());
    }

    public void evict(String entityType, String entityId) {
        cache.remove(cacheKey(entityType, entityId));
    }

    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${ai.indexing.visibility-cache.cleanup-interval:PT1M}').toMillis()}")
    public void evictExpired() {
        Instant now = Instant.now(clock);
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(properties.getTtl(), clock, now));
    }

    private void pruneOldest() {
        cache.entrySet().stream()
            .min(Comparator.comparing(entry -> entry.getValue().createdAt()))
            .ifPresent(oldest -> cache.remove(oldest.getKey()));
    }

    private String cacheKey(String entityType, String entityId) {
        return (entityType == null ? "default" : entityType.toLowerCase()) + "::" + entityId;
    }

    private record CacheEntry(String payload, Instant createdAt) {
        boolean isExpired(Duration ttl, Clock clock) {
            return isExpired(ttl, clock, Instant.now(clock));
        }

        boolean isExpired(Duration ttl, Clock clock, Instant now) {
            return createdAt.plus(ttl).isBefore(now);
        }
    }
}
