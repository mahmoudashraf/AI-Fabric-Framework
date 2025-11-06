package com.ai.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default in-memory implementation of {@link AIIntelligentCacheService} that provides
 * lightweight tagging, TTL handling, and statistics suitable for production use when
 * an external distributed cache is not configured.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultAIIntelligentCacheService implements AIIntelligentCacheService {

    private final CacheConfig cacheConfig;
    private final Clock clock;

    private final ConcurrentMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> tagIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, KeyStats> keyStatistics = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong expirationCount = new AtomicLong();
    private final AtomicLong loadCount = new AtomicLong();
    private final AtomicLong loadTime = new AtomicLong();
    private final AtomicLong totalResponseTime = new AtomicLong();

    public DefaultAIIntelligentCacheService(CacheConfig cacheConfig) {
        this(cacheConfig != null ? cacheConfig : CacheConfig.builder().build(), Clock.systemUTC());
    }

    public DefaultAIIntelligentCacheService() {
        this(CacheConfig.builder().build(), Clock.systemUTC());
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        if (!isEnabled()) {
            return null;
        }
        if (key == null) {
            return null;
        }

        long start = System.nanoTime();
        requestCount.incrementAndGet();

        CacheEntry entry = store.get(key);
        if (entry == null) {
            recordMiss(key);
            recordResponseTime(start);
            return null;
        }

        if (entry.isExpired(now())) {
            removeEntry(key, entry, true);
            recordMiss(key);
            recordResponseTime(start);
            return null;
        }

        Object value = entry.value();
        if (value == null || !type.isInstance(value)) {
            recordMiss(key);
            recordResponseTime(start);
            return null;
        }

        hitCount.incrementAndGet();
        keyStatistics.computeIfAbsent(key, k -> new KeyStats()).hits.incrementAndGet();
        recordResponseTime(start);
        return type.cast(value);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (!isEnabled() || key == null) {
            return;
        }

        Duration effectiveTtl = Optional.ofNullable(ttl).orElse(cacheConfig.getDefaultTtl());
        Instant expiry = effectiveTtl == null || effectiveTtl.isZero() || effectiveTtl.isNegative()
            ? null
            : now().plus(effectiveTtl);

        long start = System.nanoTime();
        CacheEntry entry = new CacheEntry(value, expiry, now(), new CopyOnWriteArraySet<>());
        store.put(key, entry);
        loadCount.incrementAndGet();
        loadTime.addAndGet(Duration.ofNanos(System.nanoTime() - start).toMillis());
        keyStatistics.computeIfAbsent(key, k -> new KeyStats()).loads.incrementAndGet();
        enforceCapacity();
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, cacheConfig.getDefaultTtl());
    }

    @Override
    public void evict(String key) {
        if (key == null) {
            return;
        }
        CacheEntry removed = store.remove(key);
        if (removed != null) {
            evictionCount.incrementAndGet();
            removeTagsForKey(key, removed.tags());
        }
    }

    @Override
    public void evictByPattern(String pattern) {
        if (pattern == null) {
            return;
        }
        Pattern compiled = compilePattern(pattern);
        store.keySet().stream()
            .filter(key -> compiled.matcher(key).matches())
            .toList()
            .forEach(this::evict);
    }

    @Override
    public void evictByTag(String tag) {
        if (tag == null) {
            return;
        }
        Set<String> keys = tagIndex.getOrDefault(tag, Collections.emptySet());
        keys.forEach(this::evict);
    }

    @Override
    public void evictByTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        tags.forEach(this::evictByTag);
    }

    @Override
    public CacheStatistics getStatistics() {
        cleanupExpiredEntries();

        long requests = requestCount.get();
        long hits = hitCount.get();
        long misses = missCount.get();
        long size = store.size();
        long totalLoadTime = loadTime.get();
        long loads = loadCount.get();
        long responses = totalResponseTime.get();

        return CacheStatistics.builder()
            .hitCount(hits)
            .missCount(misses)
            .hitRate(requests > 0 ? (double) hits / requests * 100.0 : 0.0)
            .missRate(requests > 0 ? (double) misses / requests * 100.0 : 0.0)
            .requestCount(requests)
            .totalSize(size)
            .maxSize(Optional.ofNullable(cacheConfig.getMaxSize()).orElse(0L))
            .memoryUsage(calculateMemoryUsage())
            .evictionCount(evictionCount.get())
            .expirationCount(expirationCount.get())
            .loadCount(loads)
            .loadTime(totalLoadTime)
            .averageLoadTime(loads > 0 ? (double) totalLoadTime / loads : 0.0)
            .totalResponseTime(responses)
            .averageResponseTime(requests > 0 ? (double) responses / requests : 0.0)
            .topKeys(getTopKeysInternal(5))
            .build();
    }

    @Override
    public void clear() {
        store.clear();
        tagIndex.clear();
        keyStatistics.clear();
        hitCount.set(0);
        missCount.set(0);
        requestCount.set(0);
        evictionCount.set(0);
        expirationCount.set(0);
        loadCount.set(0);
        loadTime.set(0);
        totalResponseTime.set(0);
    }

    @Override
    public boolean isEnabled() {
        return running.get() && (cacheConfig.getEnabled() == null || cacheConfig.getEnabled());
    }

    @Override
    public boolean exists(String key) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired(now())) {
            removeEntry(key, entry, true);
            return false;
        }
        return true;
    }

    @Override
    public long size() {
        cleanupExpiredEntries();
        return store.size();
    }

    @Override
    public long getMemoryUsage() {
        cleanupExpiredEntries();
        return calculateMemoryUsage();
    }

    @Override
    public double getHitRate() {
        long requests = requestCount.get();
        return requests > 0 ? (double) hitCount.get() / requests * 100.0 : 0.0;
    }

    @Override
    public double getMissRate() {
        long requests = requestCount.get();
        return requests > 0 ? (double) missCount.get() / requests * 100.0 : 0.0;
    }

    @Override
    public long getEvictionCount() {
        return evictionCount.get();
    }

    @Override
    public long getExpirationCount() {
        return expirationCount.get();
    }

    @Override
    public long getLoadCount() {
        return loadCount.get();
    }

    @Override
    public long getLoadTime() {
        return loadTime.get();
    }

    @Override
    public double getAverageLoadTime() {
        long loads = loadCount.get();
        return loads > 0 ? (double) loadTime.get() / loads : 0.0;
    }

    @Override
    public long getHitCount() {
        return hitCount.get();
    }

    @Override
    public long getMissCount() {
        return missCount.get();
    }

    @Override
    public long getRequestCount() {
        return requestCount.get();
    }

    @Override
    public double getAverageResponseTime() {
        long requests = requestCount.get();
        return requests > 0 ? (double) totalResponseTime.get() / requests : 0.0;
    }

    @Override
    public List<String> getTopKeys(int limit) {
        cleanupExpiredEntries();
        return getTopKeysInternal(limit);
    }

    @Override
    public List<String> getKeysByPattern(String pattern) {
        if (pattern == null) {
            return List.of();
        }
        Pattern compiled = compilePattern(pattern);
        return store.keySet().stream()
            .filter(key -> compiled.matcher(key).matches())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> getKeysByTag(String tag) {
        if (tag == null) {
            return List.of();
        }
        return new ArrayList<>(tagIndex.getOrDefault(tag, Set.of()));
    }

    @Override
    public List<String> getKeysByTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .map(tagIndex::get)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void tag(String key, String tag) {
        if (key == null || tag == null) {
            return;
        }
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return;
        }
        entry.tags().add(tag);
        tagIndex.computeIfAbsent(tag, t -> new CopyOnWriteArraySet<>()).add(key);
    }

    @Override
    public void tag(String key, List<String> tags) {
        if (tags == null) {
            return;
        }
        tags.forEach(tag -> tag(key, tag));
    }

    @Override
    public void untag(String key, String tag) {
        if (key == null || tag == null) {
            return;
        }
        CacheEntry entry = store.get(key);
        if (entry != null) {
            entry.tags().remove(tag);
        }
        Optional.ofNullable(tagIndex.get(tag)).ifPresent(set -> set.remove(key));
    }

    @Override
    public void untag(String key, List<String> tags) {
        if (tags == null) {
            return;
        }
        tags.forEach(tag -> untag(key, tag));
    }

    @Override
    public List<String> getTags(String key) {
        if (key == null) {
            return List.of();
        }
        CacheEntry entry = store.get(key);
        return entry != null ? new ArrayList<>(entry.tags()) : List.of();
    }

    @Override
    public List<String> getAllTags() {
        return new ArrayList<>(tagIndex.keySet());
    }

    @Override
    public CacheConfig getConfiguration() {
        return cacheConfig;
    }

    @Override
    public void updateConfiguration(CacheConfig config) {
        if (config == null) {
            return;
        }
        cacheConfig.setEnabled(config.getEnabled());
        cacheConfig.setDefaultTtl(config.getDefaultTtl());
        cacheConfig.setMaxSize(config.getMaxSize());
        cacheConfig.setEvictionPolicy(config.getEvictionPolicy());
    }

    @Override
    public void warmUp(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        data.forEach(this::put);
    }

    @Override
    public void preload(Map<String, Object> data) {
        warmUp(data);
    }

    @Override
    public void refresh(String key) {
        if (key == null) {
            return;
        }
        CacheEntry entry = store.get(key);
        if (entry != null) {
            Duration ttl = cacheConfig.getDefaultTtl();
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                CacheEntry refreshed = new CacheEntry(
                    entry.value(),
                    now().plus(ttl),
                    entry.createdAt(),
                    new CopyOnWriteArraySet<>(entry.tags())
                );
                store.put(key, refreshed);
            }
        }
    }

    @Override
    public void refreshByPattern(String pattern) {
        getKeysByPattern(pattern).forEach(this::refresh);
    }

    @Override
    public void refreshByTag(String tag) {
        getKeysByTag(tag).forEach(this::refresh);
    }

    @Override
    public void refreshByTags(List<String> tags) {
        getKeysByTags(tags).forEach(this::refresh);
    }

    @Override
    public Map<String, Object> getHealthStatus() {
        cleanupExpiredEntries();
        boolean healthy = isEnabled();
        return Map.of(
            "status", healthy ? "UP" : "DOWN",
            "size", store.size(),
            "hitRate", getHitRate(),
            "evictionCount", evictionCount.get()
        );
    }

    @Override
    public Map<String, Object> getMetrics() {
        cleanupExpiredEntries();
        return Map.of(
            "enabled", isEnabled(),
            "size", store.size(),
            "memoryUsage", calculateMemoryUsage(),
            "hits", hitCount.get(),
            "misses", missCount.get(),
            "requests", requestCount.get(),
            "evictions", evictionCount.get(),
            "expirations", expirationCount.get()
        );
    }

    @Override
    public List<String> getRecommendations() {
        List<String> recommendations = new ArrayList<>();
        if (getHitRate() < 50.0) {
            recommendations.add("Consider widening TTL or reviewing cache key strategy to improve hit rate.");
        }
        if (store.size() > Optional.ofNullable(cacheConfig.getMaxSize()).orElse(Long.MAX_VALUE) * 0.9) {
            recommendations.add("Cache nearing capacity; evaluate eviction policy or max size.");
        }
        if (evictionCount.get() > 0 && cacheConfig.getEvictionPolicy() != null) {
            recommendations.add("Monitor eviction spikes; validate eviction policy " + cacheConfig.getEvictionPolicy());
        }
        return recommendations;
    }

    @Override
    public void optimize() {
        cleanupExpiredEntries();
        enforceCapacity();
    }

    @Override
    public void resetStatistics() {
        hitCount.set(0);
        missCount.set(0);
        requestCount.set(0);
        evictionCount.set(0);
        expirationCount.set(0);
        loadCount.set(0);
        loadTime.set(0);
        totalResponseTime.set(0);
        keyStatistics.clear();
    }

    @Override
    public Map<String, Object> exportData() {
        cleanupExpiredEntries();
        return store.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value()));
    }

    @Override
    public void importData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        data.forEach(this::put);
    }

    @Override
    public Map<String, Object> backup() {
        Map<String, Object> tags = tagIndex.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));
        return Map.of(
            "timestamp", now().toString(),
            "data", exportData(),
            "tags", tags
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restore(Map<String, Object> backup) {
        if (backup == null || backup.isEmpty()) {
            return;
        }
        Object rawData = backup.get("data");
        if (rawData instanceof Map<?, ?> map) {
            clear();
            map.forEach((k, v) -> put(String.valueOf(k), v));
        }
        Object rawTags = backup.get("tags");
        if (rawTags instanceof Map<?, ?> map) {
            ((Map<?, ?>) rawTags).forEach((tag, keys) -> {
                if (tag != null && keys instanceof Collection<?> collection) {
                    collection.forEach(key -> tag(String.valueOf(key), String.valueOf(tag)));
                }
            });
        }
    }

    @Override
    public void shutdown() {
        running.set(false);
        clear();
    }

    @Override
    public CacheStatistics getCacheStatistics() {
        return getStatistics();
    }

    @Override
    public void clearAllCaches() {
        clear();
    }

    private void recordMiss(String key) {
        missCount.incrementAndGet();
        keyStatistics.computeIfAbsent(key, k -> new KeyStats()).misses.incrementAndGet();
    }

    private void recordResponseTime(long startNano) {
        totalResponseTime.addAndGet(Duration.ofNanos(System.nanoTime() - startNano).toMillis());
    }

    private void removeEntry(String key, CacheEntry entry, boolean expired) {
        store.remove(key);
        removeTagsForKey(key, entry.tags());
        if (expired) {
            expirationCount.incrementAndGet();
        } else {
            evictionCount.incrementAndGet();
        }
    }

    private void removeTagsForKey(String key, Set<String> tags) {
        if (tags == null) {
            return;
        }
        tags.forEach(tag -> Optional.ofNullable(tagIndex.get(tag)).ifPresent(set -> set.remove(key)));
    }

    private void enforceCapacity() {
        Long maxSize = cacheConfig.getMaxSize();
        if (maxSize == null || maxSize <= 0) {
            return;
        }
        while (store.size() > maxSize) {
            store.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().createdAt()))
                .ifPresent(entry -> removeEntry(entry.getKey(), entry.getValue(), false));
        }
    }

    private void cleanupExpiredEntries() {
        Instant now = now();
        store.forEach((key, entry) -> {
            if (entry.isExpired(now)) {
                removeEntry(key, entry, true);
            }
        });
    }

    private List<String> getTopKeysInternal(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return keyStatistics.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().hits.get(), a.getValue().hits.get()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private long calculateMemoryUsage() {
        return store.entrySet().stream()
            .mapToLong(e -> estimateSize(e.getKey()) + estimateSize(e.getValue().value()))
            .sum();
    }

    private long estimateSize(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof String str) {
            return str.length() * 2L;
        }
        if (value instanceof byte[] arr) {
            return arr.length;
        }
        if (value instanceof Collection<?> collection) {
            return collection.size() * 32L;
        }
        if (value instanceof Map<?, ?> map) {
            return map.size() * 64L;
        }
        return 64L;
    }

    private Pattern compilePattern(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (Exception ex) {
            return Pattern.compile(Pattern.quote(pattern));
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private record CacheEntry(Object value, Instant expiresAt, Instant createdAt, Set<String> tags) {
        boolean isExpired(Instant now) {
            return expiresAt != null && expiresAt.isBefore(now);
        }
    }

    private static class KeyStats {
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();
        private final AtomicLong loads = new AtomicLong();
    }
}
