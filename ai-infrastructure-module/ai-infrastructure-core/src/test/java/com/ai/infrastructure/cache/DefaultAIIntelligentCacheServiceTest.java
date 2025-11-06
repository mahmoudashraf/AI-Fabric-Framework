package com.ai.infrastructure.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DefaultAIIntelligentCacheServiceTest {

    private DefaultAIIntelligentCacheService cacheService;

    @BeforeEach
    void setUp() {
        CacheConfig cacheConfig = CacheConfig.builder()
            .enabled(true)
            .defaultTtl(Duration.ofSeconds(1))
            .maxSize(100L)
            .build();
        cacheService = new DefaultAIIntelligentCacheService(cacheConfig);
    }

    @Test
    @DisplayName("put/get round-trip returns cached value")
    void putAndGetRoundTrip() {
        cacheService.put("greeting", "hello-world");

        String result = cacheService.get("greeting", String.class);

        assertEquals("hello-world", result);
        assertTrue(cacheService.exists("greeting"));
        assertThat(cacheService.getHitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("expired entries are removed on access")
    void expiredEntriesArePurged() throws InterruptedException {
        CacheConfig shortTtl = CacheConfig.builder()
            .enabled(true)
            .defaultTtl(Duration.ofMillis(50))
            .build();
        cacheService = new DefaultAIIntelligentCacheService(shortTtl);

        cacheService.put("temp", "value", Duration.ofMillis(50));
        assertNotNull(cacheService.get("temp", String.class));

        TimeUnit.MILLISECONDS.sleep(75);

        assertNull(cacheService.get("temp", String.class));
        assertFalse(cacheService.exists("temp"));
        assertThat(cacheService.getExpirationCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("tagging operations index keys")
    void taggingAddsIndex() {
        cacheService.put("product:1", Map.of("id", 1));
        cacheService.tag("product:1", "products");

        assertThat(cacheService.getKeysByTag("products")).containsExactly("product:1");
        cacheService.evictByTag("products");
        assertFalse(cacheService.exists("product:1"));
    }

    @Test
    @DisplayName("statistics capture cache usage")
    void statisticsCaptureUsage() {
        cacheService.put("alpha", "a");
        cacheService.get("alpha", String.class);
        cacheService.get("missing", String.class);

        CacheStatistics stats = cacheService.getStatistics();

        assertThat(stats.getHitCount()).isEqualTo(1);
        assertThat(stats.getMissCount()).isEqualTo(1);
        assertThat(stats.getRequestCount()).isEqualTo(2);
        assertThat(stats.getTopKeys()).contains("alpha");
    }

    @Test
    @DisplayName("backup and restore rehydrates cache contents")
    void backupAndRestore() {
        cacheService.put("session", "token");
        cacheService.tag("session", "auth");

        Map<String, Object> backup = cacheService.backup();
        cacheService.clear();

        cacheService.restore(backup);

        assertEquals("token", cacheService.get("session", String.class));
        assertThat(cacheService.getKeysByTag("auth")).contains("session");
    }

    @Test
    @DisplayName("pattern eviction removes matching keys")
    void evictByPatternRemovesMatches() {
        cacheService.put("user:1", "one");
        cacheService.put("user:2", "two");
        cacheService.put("order:1", "three");

        cacheService.evictByPattern("user:.*");

        assertNull(cacheService.get("user:1", String.class));
        assertNull(cacheService.get("user:2", String.class));
        assertNotNull(cacheService.get("order:1", String.class));
    }
}
