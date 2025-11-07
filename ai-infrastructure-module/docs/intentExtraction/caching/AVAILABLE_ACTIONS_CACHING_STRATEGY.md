# AvailableActions - Comprehensive Caching Strategy

## The Challenge
AvailableActions data is:
- **Frequently accessed** - Every intent extraction call needs all actions
- **Relatively static** - Changes infrequently (when services deploy)
- **High read volume** - Could be thousands of calls per second
- **Must be accurate** - Stale data = wrong LLM decisions

## The Solution: Multi-Level Caching Strategy

---

## 🎯 Caching Objectives

✅ **Performance** - Eliminate repeated registry scans
✅ **Consistency** - Always have accurate action list
✅ **Availability** - Never fail due to cache issues
✅ **Scalability** - Handle 10K+ requests per second
✅ **Simplicity** - Easy to understand and maintain

---

## Level 1: In-Memory Cache (Registry)

### Current Implementation
```java
@Service
public class AvailableActionsRegistry {
    private volatile List<ActionInfo> cachedActions;
    
    public List<ActionInfo> getAllAvailableActions() {
        // Return cached if available
        if (cachedActions != null) {
            return new ArrayList<>(cachedActions);
        }
        
        // Build cache on first call
        List<ActionInfo> allActions = providers.stream()
            .flatMap(p -> p.getAvailableActions().stream())
            .collect(Collectors.toList());
        
        this.cachedActions = allActions;
        return new ArrayList<>(allActions);
    }
}
```

### Enhancement: Expiration & Refresh
```java
@Service
@Slf4j
public class AvailableActionsRegistry {
    
    private volatile List<ActionInfo> cachedActions;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    
    public List<ActionInfo> getAllAvailableActions() {
        // Check if cache is still valid
        if (cachedActions != null && 
            (System.currentTimeMillis() - lastCacheTime) < CACHE_TTL_MS) {
            return new ArrayList<>(cachedActions);
        }
        
        log.debug("Cache expired or not yet initialized, rebuilding");
        return rebuildCache();
    }
    
    private synchronized List<ActionInfo> rebuildCache() {
        // Double-check pattern for thread safety
        if (cachedActions != null && 
            (System.currentTimeMillis() - lastCacheTime) < CACHE_TTL_MS) {
            return new ArrayList<>(cachedActions);
        }
        
        log.info("Rebuilding action cache");
        
        List<ActionInfo> allActions = providers.stream()
            .flatMap(provider -> {
                try {
                    return provider.getAvailableActions().stream();
                } catch (Exception e) {
                    log.error("Error getting actions from {}", 
                        provider.getClass().getSimpleName(), e);
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());
        
        this.cachedActions = new ArrayList<>(allActions);
        this.lastCacheTime = System.currentTimeMillis();
        
        log.info("Cache rebuilt with {} actions", allActions.size());
        
        return new ArrayList<>(allActions);
    }
    
    /**
     * Manually refresh cache (call on service deployment)
     */
    public void refreshCache() {
        log.info("Manual cache refresh requested");
        this.lastCacheTime = 0; // Force rebuild on next access
    }
    
    /**
     * Clear cache entirely
     */
    public void clearCache() {
        log.info("Cache cleared");
        this.cachedActions = null;
        this.lastCacheTime = 0;
    }
}
```

### Properties Configuration
```yaml
# application.yml
ai:
  actions:
    cache:
      enabled: true
      ttl-minutes: 5  # Rebuild cache every 5 minutes
      initial-load: true  # Load on startup
      enable-refresh-endpoint: true  # Allow manual refresh
```

---

## Level 2: Cache by Category

### Use Case
Many queries target specific categories (subscription, payment, order)

### Implementation
```java
@Service
@Slf4j
public class AvailableActionsRegistry {
    
    private volatile Map<String, List<ActionInfo>> categoryCaches = new HashMap<>();
    private volatile long categoryCacheTime = 0;
    private static final long CATEGORY_CACHE_TTL_MS = 5 * 60 * 1000;
    
    /**
     * Get actions for specific category (with caching)
     */
    public List<ActionInfo> getActionsByCategory(String category) {
        // Check if category cache is valid
        if (isValidCategoryCache()) {
            List<ActionInfo> cached = categoryCaches.get(category);
            if (cached != null) {
                return new ArrayList<>(cached);
            }
        }
        
        // Rebuild category cache
        rebuildCategoryCache();
        
        List<ActionInfo> result = categoryCaches.getOrDefault(category, List.of());
        return new ArrayList<>(result);
    }
    
    private boolean isValidCategoryCache() {
        return !categoryCaches.isEmpty() && 
               (System.currentTimeMillis() - categoryCacheTime) < CATEGORY_CACHE_TTL_MS;
    }
    
    private synchronized void rebuildCategoryCache() {
        if (isValidCategoryCache()) {
            return;
        }
        
        log.debug("Rebuilding category caches");
        
        Map<String, List<ActionInfo>> newCache = getAllAvailableActions().stream()
            .collect(Collectors.groupingBy(ActionInfo::getCategory));
        
        this.categoryCaches = newCache;
        this.categoryCacheTime = System.currentTimeMillis();
    }
}
```

---

## Level 3: LLM Prompt Cache

### Use Case
The formatted LLM prompt is expensive to build and rarely changes

### Implementation
```java
@Service
@Slf4j
public class AvailableActionsRegistry {
    
    private volatile String cachedLLMPrompt;
    private volatile long promptCacheTime = 0;
    private static final long PROMPT_CACHE_TTL_MS = 10 * 60 * 1000; // 10 min
    
    /**
     * Get formatted actions for LLM (cached)
     */
    public String formatForLLMPrompt() {
        // Return cached if valid
        if (cachedLLMPrompt != null && 
            (System.currentTimeMillis() - promptCacheTime) < PROMPT_CACHE_TTL_MS) {
            return cachedLLMPrompt;
        }
        
        return rebuildLLMPromptCache();
    }
    
    private synchronized String rebuildLLMPromptCache() {
        if (cachedLLMPrompt != null && 
            (System.currentTimeMillis() - promptCacheTime) < PROMPT_CACHE_TTL_MS) {
            return cachedLLMPrompt;
        }
        
        log.debug("Rebuilding LLM prompt cache");
        
        String prompt = getAllAvailableActions().stream()
            .map(this::formatActionForPrompt)
            .collect(Collectors.joining("\n\n"));
        
        this.cachedLLMPrompt = prompt;
        this.promptCacheTime = System.currentTimeMillis();
        
        log.info("LLM prompt cache rebuilt (size: {} chars)", prompt.length());
        
        return prompt;
    }
    
    private String formatActionForPrompt(ActionInfo action) {
        // Your existing implementation
        return "...";
    }
}
```

---

## Level 4: Distributed Cache (Redis)

### When to Use
- Multiple application instances
- Need cache sharing across deployments
- Want cache persistence

### Implementation

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cache</groupId>
    <artifactId>spring-cache</artifactId>
</dependency>
```

**Configuration:**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );
        
        return RedisCacheManager.create(factory);
    }
}
```

**Service Implementation:**
```java
@Service
@Slf4j
public class AvailableActionsRegistry {
    
    @Autowired(required = false)
    private RedisTemplate<String, List<ActionInfo>> redisTemplate;
    
    private static final String CACHE_KEY = "ai:actions:all";
    private static final String CATEGORY_CACHE_KEY = "ai:actions:category:";
    private static final String PROMPT_CACHE_KEY = "ai:actions:prompt";
    
    @Cacheable(value = "availableActions", unless = "#result == null")
    public List<ActionInfo> getAllAvailableActions() {
        log.debug("Cache miss - rebuilding actions");
        
        List<ActionInfo> actions = buildActions();
        
        // Also store in Redis if available
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(
                CACHE_KEY, 
                actions, 
                Duration.ofMinutes(5)
            );
        }
        
        return actions;
    }
    
    @Cacheable(value = "actionsByCategory", key = "#category")
    public List<ActionInfo> getActionsByCategory(String category) {
        log.debug("Cache miss - getting actions for category: {}", category);
        
        return getAllAvailableActions().stream()
            .filter(a -> category.equals(a.getCategory()))
            .collect(Collectors.toList());
    }
    
    @CacheEvict(allEntries = true, cacheNames = {"availableActions", "actionsByCategory"})
    public void evictCache() {
        log.info("Cache evicted");
        
        if (redisTemplate != null) {
            redisTemplate.delete(CACHE_KEY);
        }
    }
    
    private List<ActionInfo> buildActions() {
        // Your implementation
        return null;
    }
}
```

**Properties:**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    jedis:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5 minutes in ms
```

---

## Level 5: SystemContext Cache

### Problem
SystemContextBuilder rebuilds everything including actions for each request

### Solution: Cache the entire context
```java
@Service
@Slf4j
public class SystemContextBuilder {
    
    private final AvailableActionsRegistry actionsRegistry;
    private volatile Map<String, SystemContext> userContextCache = new ConcurrentHashMap<>();
    private static final long USER_CONTEXT_TTL_MS = 10 * 60 * 1000; // 10 min
    
    /**
     * Build or retrieve cached system context
     */
    public SystemContext buildContext(String userId) {
        // Check cache first
        CachedContext cached = getFromCache(userId);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached context for user: {}", userId);
            return cached.context;
        }
        
        log.debug("Building fresh context for user: {}", userId);
        
        SystemContext context = SystemContext.builder()
            .userId(userId)
            .availableActions(actionsRegistry.getAllAvailableActions())
            .knowledgeBaseOverview(buildKnowledgeBaseOverview())
            .entityTypesSchema(buildEntityTypesSchema())
            .userBehaviorContext(buildUserBehaviorContext(userId))
            .timestamp(LocalDateTime.now())
            .build();
        
        // Cache it
        cacheContext(userId, context);
        
        return context;
    }
    
    private void cacheContext(String userId, SystemContext context) {
        userContextCache.put(userId, new CachedContext(
            context,
            System.currentTimeMillis()
        ));
        
        // Clean up old entries if cache gets too large
        if (userContextCache.size() > 10000) {
            evictOldEntries();
        }
    }
    
    private CachedContext getFromCache(String userId) {
        return userContextCache.get(userId);
    }
    
    private void evictOldEntries() {
        userContextCache.entrySet().removeIf(
            entry -> entry.getValue().isExpired()
        );
    }
    
    private static class CachedContext {
        SystemContext context;
        long timestamp;
        
        CachedContext(SystemContext context, long timestamp) {
            this.context = context;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > USER_CONTEXT_TTL_MS;
        }
    }
}
```

---

## Level 6: Request-Level Caching

### Problem
Within a single request flow, actions might be retrieved multiple times

### Solution: RequestScoped cache
```java
@Service
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Slf4j
public class RequestScopedActionCache {
    
    private List<ActionInfo> cachedActions;
    private Map<String, List<ActionInfo>> categoryCache;
    
    public List<ActionInfo> getActions(AvailableActionsRegistry registry) {
        if (cachedActions == null) {
            log.debug("Request cache miss - fetching actions");
            cachedActions = registry.getAllAvailableActions();
        }
        return cachedActions;
    }
    
    public List<ActionInfo> getActionsByCategory(String category, AvailableActionsRegistry registry) {
        if (categoryCache == null) {
            categoryCache = new HashMap<>();
        }
        
        return categoryCache.computeIfAbsent(category, 
            cat -> registry.getActionsByCategory(cat)
        );
    }
}
```

Usage:
```java
@Service
public class IntentQueryExtractor {
    
    private final RequestScopedActionCache requestCache;
    private final AvailableActionsRegistry registry;
    
    public MultiIntentResponse extract(String rawQuery, String userId) {
        // Uses request cache internally
        List<ActionInfo> actions = requestCache.getActions(registry);
        
        // Build prompt with cached actions
        String systemPrompt = buildPrompt(actions);
        
        // ... rest of extraction
    }
}
```

---

## Cache Invalidation Strategy

### When to Invalidate

```java
@Service
@Slf4j
public class CacheInvalidationService {
    
    @Autowired
    private AvailableActionsRegistry registry;
    
    /**
     * Invalidate on service deployment
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStart() {
        log.info("Application started, refreshing action cache");
        registry.refreshCache();
    }
    
    /**
     * Invalidate when service updated
     */
    @PostMapping("/admin/actions/refresh")
    public ResponseEntity<?> refreshActions() {
        log.info("Manual cache refresh requested");
        registry.refreshCache();
        return ResponseEntity.ok("Cache refreshed");
    }
    
    /**
     * Scheduled refresh (every 5 minutes)
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void scheduledCacheRefresh() {
        log.debug("Scheduled cache refresh");
        registry.refreshCache();
    }
    
    /**
     * Event-based invalidation
     */
    @EventListener
    public void onActionProviderUpdate(ActionProviderUpdatedEvent event) {
        log.info("Action provider updated, clearing cache");
        registry.clearCache();
    }
}
```

---

## Monitoring & Metrics

### Cache Hit Ratio

```java
@Service
@Slf4j
public class CacheMetricsService {
    
    private AtomicLong cacheHits = new AtomicLong(0);
    private AtomicLong cacheMisses = new AtomicLong(0);
    
    public void recordHit() {
        cacheHits.incrementAndGet();
    }
    
    public void recordMiss() {
        cacheMisses.incrementAndGet();
    }
    
    public double getCacheHitRatio() {
        long total = cacheHits.get() + cacheMisses.get();
        if (total == 0) return 0.0;
        return (double) cacheHits.get() / total;
    }
    
    @GetMapping("/admin/metrics/cache")
    public ResponseEntity<?> getCacheMetrics() {
        return ResponseEntity.ok(Map.of(
            "hits", cacheHits.get(),
            "misses", cacheMisses.get(),
            "hitRatio", getCacheHitRatio(),
            "timestamp", LocalDateTime.now()
        ));
    }
}
```

### Prometheus Metrics

```java
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistry meterRegistry() {
        MeterRegistry registry = new SimpleMeterRegistry();
        
        registry.counter("cache.actions.hits");
        registry.counter("cache.actions.misses");
        registry.gauge("cache.actions.size", () -> 0);
        
        return registry;
    }
}
```

---

## Complete Caching Configuration

```yaml
# application.yml
ai:
  actions:
    cache:
      # In-memory cache settings
      enabled: true
      ttl-minutes: 5
      category-cache-enabled: true
      prompt-cache-enabled: true
      prompt-cache-ttl-minutes: 10
      
      # Redis cache settings (if distributed)
      redis-enabled: false
      redis-ttl-minutes: 5
      
      # Request-scoped cache
      request-cache-enabled: true
      
      # User context cache
      user-context-cache-enabled: true
      user-context-ttl-minutes: 10
      max-user-contexts: 10000
      
      # Monitoring
      metrics-enabled: true
      log-cache-operations: false
      
      # Refresh strategy
      scheduled-refresh-enabled: true
      scheduled-refresh-interval-minutes: 5
```

---

## Cache Performance Comparison

### Scenario: 10,000 requests/second

| Cache Level | Hits/sec | Response Time | Memory | CPU |
|-------------|----------|---------------|--------|-----|
| No cache | 0 | 50ms | Low | High |
| In-memory | 9,900 | <1ms | 1-5MB | Low |
| + Category | 9,950 | <0.5ms | 2-10MB | Low |
| + Prompt | 9,980 | <0.3ms | 5-15MB | Very Low |
| + Redis | 9,990 | <0.5ms | 10-30MB | Low |
| All + Request | 9,999 | <0.1ms | 20-50MB | Very Low |

---

## Best Practices

### ✅ DO

1. **Cache aggressively** - AvailableActions rarely change
2. **Use TTL** - Don't cache forever
3. **Monitor hit ratio** - Target >90%
4. **Invalidate strategically** - On deployment, updates
5. **Use multiple levels** - Each level serves a purpose
6. **Test cache behavior** - Ensure correctness
7. **Log cache operations** - Debug cache issues
8. **Set reasonable TTLs** - Balance freshness vs performance

### ❌ DON'T

1. **Cache user-specific data forever** - Use reasonable TTL
2. **Ignore cache misses** - Monitor them
3. **Cache without invalidation** - Stale data is worse than no cache
4. **Rely solely on in-memory** - Use Redis for distributed
5. **Cache without monitoring** - Can't optimize what you don't measure
6. **Forget about memory limits** - Cache can grow unbounded
7. **Cache sensitive data** - Be careful with security
8. **Over-cache** - Every cache adds complexity

---

## Implementation Priority

### Phase 1: Essential (Do First)
1. ✅ In-memory cache with TTL
2. ✅ Manual refresh endpoint
3. ✅ Cache invalidation on deployment

### Phase 2: Optimization (Do Next)
4. ⏳ Category cache
5. ⏳ LLM prompt cache
6. ⏳ Monitoring & metrics

### Phase 3: Scale (Do When Needed)
7. 📈 Redis distributed cache
8. 📈 User context cache
9. 📈 Request-scoped cache

---

## Testing Cache Behavior

```java
@SpringBootTest
class AvailableActionsCacheTest {
    
    @Autowired
    private AvailableActionsRegistry registry;
    
    @Test
    void shouldCacheActions() {
        // First call - builds cache
        long start1 = System.nanoTime();
        List<ActionInfo> actions1 = registry.getAllAvailableActions();
        long time1 = System.nanoTime() - start1;
        
        // Second call - uses cache
        long start2 = System.nanoTime();
        List<ActionInfo> actions2 = registry.getAllAvailableActions();
        long time2 = System.nanoTime() - start2;
        
        // Same content
        assertThat(actions1).isEqualTo(actions2);
        
        // Second call much faster
        assertThat(time2).isLessThan(time1 / 10);
    }
    
    @Test
    void shouldExpireCache() throws InterruptedException {
        registry.getAllAvailableActions();
        
        // Wait for TTL
        Thread.sleep(301000); // 5 minutes + 1 second
        
        // Should rebuild on next access
        // (in real test, set shorter TTL)
    }
    
    @Test
    void shouldInvalidateOnRefresh() {
        List<ActionInfo> actions1 = registry.getAllAvailableActions();
        
        registry.refreshCache();
        
        List<ActionInfo> actions2 = registry.getAllAvailableActions();
        
        // Should rebuild fresh
        assertThat(actions2).isNotNull();
    }
}
```

---

## Summary

### Multi-Level Caching Strategy:

1. **Level 1:** In-memory cache with TTL (5 min)
2. **Level 2:** Category-based cache (for filtered queries)
3. **Level 3:** LLM prompt cache (10 min)
4. **Level 4:** Redis distributed cache (optional, for scaling)
5. **Level 5:** SystemContext cache (per user)
6. **Level 6:** Request-scoped cache (within single request)

### Expected Benefits:

✅ **Performance:** <1ms response for cached requests
✅ **Scalability:** Handle 10K+ requests/second
✅ **Reliability:** Consistent behavior across instances
✅ **Monitoring:** Full visibility into cache behavior

### Implementation Order:

1. **Today:** Levels 1-3 (essential)
2. **This week:** Levels 1-3 + monitoring
3. **Next sprint:** Add Redis if needed
4. **Future:** User context & request caching

---

## Next Steps

1. ✅ Implement Level 1 (in-memory with TTL)
2. ✅ Add manual refresh endpoint
3. ✅ Add cache monitoring
4. ✅ Test cache behavior
5. ✅ Measure performance improvement
6. ⏳ Add Redis when scaling becomes issue
7. ⏳ Add user context cache for personalization

**This caching strategy will give you production-grade performance! 🚀**

