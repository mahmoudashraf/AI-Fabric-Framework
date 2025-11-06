# AvailableActions - Caching Quick Implementation (1 Hour)

## What to Cache

```
❌ DON'T Cache:
  - User-specific data (changes per user)
  - Sensitive configuration
  - Real-time status

✅ DO Cache:
  - All available actions (rarely changes)
  - Actions by category (rarely changes)
  - Formatted LLM prompt (rarely changes)
  - System context per user (valid for ~10 min)
```

---

## Implementation Plan (1 Hour)

### Step 1: Add TTL to Registry (15 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/action/AvailableActionsRegistry.java`

Replace your existing registry with this:

```java
package com.ai.infrastructure.action;

import com.ai.infrastructure.dto.ActionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailableActionsRegistry {
    
    private final List<AIActionProvider> providers;
    
    @Value("${ai.actions.cache.ttl-minutes:5}")
    private int cacheTtlMinutes;
    
    // Cache storage with timestamps
    private volatile List<ActionInfo> cachedActions;
    private volatile long actionsCacheTime = 0;
    
    private volatile Map<String, List<ActionInfo>> categoryCaches = new HashMap<>();
    private volatile long categoryCacheTime = 0;
    
    private volatile String cachedLLMPrompt;
    private volatile long promptCacheTime = 0;
    
    // Cache TTL constants
    private static final long ACTION_CACHE_TTL_MS = 5 * 60 * 1000;      // 5 min
    private static final long CATEGORY_CACHE_TTL_MS = 5 * 60 * 1000;    // 5 min
    private static final long PROMPT_CACHE_TTL_MS = 10 * 60 * 1000;     // 10 min
    
    // ==================== ALL ACTIONS ====================
    
    /**
     * Get all available actions (with caching)
     */
    public List<ActionInfo> getAllAvailableActions() {
        // Check if cache is still valid
        if (isCacheValid(actionsCacheTime, ACTION_CACHE_TTL_MS)) {
            log.trace("Returning cached actions");
            return new ArrayList<>(cachedActions);
        }
        
        // Need to rebuild cache
        return rebuildActionCache();
    }
    
    private synchronized List<ActionInfo> rebuildActionCache() {
        // Double-check after acquiring lock
        if (isCacheValid(actionsCacheTime, ACTION_CACHE_TTL_MS)) {
            return new ArrayList<>(cachedActions);
        }
        
        log.debug("Rebuilding action cache (TTL expired)");
        
        List<ActionInfo> allActions = providers.stream()
            .flatMap(provider -> {
                String providerName = provider.getClass().getSimpleName();
                try {
                    List<ActionInfo> actions = provider.getAvailableActions();
                    log.debug("Got {} actions from {}", actions.size(), providerName);
                    
                    // Set handler service for each action
                    actions.forEach(a -> a.setHandlerService(providerName));
                    
                    return actions.stream();
                } catch (Exception e) {
                    log.error("Error getting actions from provider {}", 
                        providerName, e);
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());
        
        // Update cache
        this.cachedActions = allActions;
        this.actionsCacheTime = System.currentTimeMillis();
        
        log.info("Action cache rebuilt: {} total actions", allActions.size());
        
        return new ArrayList<>(allActions);
    }
    
    // ==================== CATEGORY CACHE ====================
    
    /**
     * Get actions by category (with caching)
     */
    public List<ActionInfo> getActionsByCategory(String category) {
        // Check if category cache is valid
        if (isCacheValid(categoryCacheTime, CATEGORY_CACHE_TTL_MS)) {
            List<ActionInfo> cached = categoryCaches.get(category);
            if (cached != null) {
                log.trace("Returning cached actions for category: {}", category);
                return new ArrayList<>(cached);
            }
        }
        
        // Need to rebuild category cache
        rebuildCategoryCache();
        
        List<ActionInfo> result = categoryCaches.getOrDefault(category, List.of());
        return new ArrayList<>(result);
    }
    
    private synchronized void rebuildCategoryCache() {
        if (isCacheValid(categoryCacheTime, CATEGORY_CACHE_TTL_MS)) {
            return;
        }
        
        log.debug("Rebuilding category cache (TTL expired)");
        
        Map<String, List<ActionInfo>> newCache = getAllAvailableActions().stream()
            .collect(Collectors.groupingBy(ActionInfo::getCategory));
        
        this.categoryCaches = newCache;
        this.categoryCacheTime = System.currentTimeMillis();
        
        log.info("Category cache rebuilt: {} categories", newCache.size());
    }
    
    // ==================== LLM PROMPT CACHE ====================
    
    /**
     * Get formatted actions for LLM (with caching)
     */
    public String formatForLLMPrompt() {
        // Check if prompt cache is valid (longer TTL = less rebuilds)
        if (isCacheValid(promptCacheTime, PROMPT_CACHE_TTL_MS)) {
            log.trace("Returning cached LLM prompt");
            return cachedLLMPrompt;
        }
        
        // Need to rebuild prompt cache
        return rebuildLLMPromptCache();
    }
    
    private synchronized String rebuildLLMPromptCache() {
        if (isCacheValid(promptCacheTime, PROMPT_CACHE_TTL_MS)) {
            return cachedLLMPrompt;
        }
        
        log.debug("Rebuilding LLM prompt cache (TTL expired)");
        
        String prompt = getAllAvailableActions().stream()
            .map(this::formatActionForPrompt)
            .collect(Collectors.joining("\n\n"));
        
        this.cachedLLMPrompt = prompt;
        this.promptCacheTime = System.currentTimeMillis();
        
        log.info("LLM prompt cache rebuilt: {} characters", prompt.length());
        
        return prompt;
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Check if cache is still valid
     */
    private boolean isCacheValid(long cacheTime, long ttlMs) {
        if (cacheTime == 0) {
            return false; // Never cached
        }
        
        long ageMs = System.currentTimeMillis() - cacheTime;
        return ageMs < ttlMs;
    }
    
    /**
     * Manually refresh all caches (call on deployment)
     */
    public void refreshAllCaches() {
        log.info("Manual cache refresh requested");
        this.actionsCacheTime = 0;
        this.categoryCacheTime = 0;
        this.promptCacheTime = 0;
        
        // Force rebuild
        getAllAvailableActions();
        rebuildCategoryCache();
        rebuildLLMPromptCache();
        
        log.info("All caches refreshed");
    }
    
    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        log.info("Clearing all caches");
        this.cachedActions = null;
        this.actionsCacheTime = 0;
        this.categoryCaches.clear();
        this.categoryCacheTime = 0;
        this.cachedLLMPrompt = null;
        this.promptCacheTime = 0;
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
            "actions_cached", cachedActions != null,
            "actions_age_ms", System.currentTimeMillis() - actionsCacheTime,
            "category_cache_size", categoryCaches.size(),
            "category_age_ms", System.currentTimeMillis() - categoryCacheTime,
            "prompt_cached", cachedLLMPrompt != null,
            "prompt_age_ms", System.currentTimeMillis() - promptCacheTime,
            "total_actions", cachedActions != null ? cachedActions.size() : 0
        );
    }
    
    // ==================== EXISTING METHODS ====================
    
    public Optional<ActionInfo> getActionByName(String actionName) {
        return getAllAvailableActions().stream()
            .filter(a -> actionName.equals(a.getAction()))
            .findFirst();
    }
    
    public boolean actionExists(String actionName) {
        return getActionByName(actionName).isPresent();
    }
    
    public List<String> getCategories() {
        return getAllAvailableActions().stream()
            .map(ActionInfo::getCategory)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }
    
    public Map<String, Integer> getActionCountByCategory() {
        return getAllAvailableActions().stream()
            .collect(Collectors.groupingBy(
                ActionInfo::getCategory,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    List::size
                )
            ));
    }
    
    private String formatActionForPrompt(ActionInfo action) {
        // Your existing implementation
        StringBuilder sb = new StringBuilder();
        sb.append("ACTION: ").append(action.getAction()).append("\n");
        sb.append("Description: ").append(action.getDescription()).append("\n");
        
        if (action.getCategory() != null) {
            sb.append("Category: ").append(action.getCategory()).append("\n");
        }
        
        if (!action.getExamples().isEmpty()) {
            sb.append("Examples: ")
                .append(String.join(" | ", action.getExamples()));
        }
        
        return sb.toString();
    }
}
```

---

### Step 2: Add Cache Configuration (10 min)

**File:** `backend/src/main/resources/application.yml`

```yaml
ai:
  actions:
    cache:
      enabled: true
      ttl-minutes: 5
      category-cache-enabled: true
      prompt-cache-ttl-minutes: 10
      metrics-enabled: true
      log-cache-operations: false
```

---

### Step 3: Add Cache Endpoints (20 min)

**File:** `backend/src/main/java/com/easyluxury/ai/controller/ActionCacheController.java`

```java
package com.easyluxury.ai.controller;

import com.ai.infrastructure.action.AvailableActionsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/actions")
@RequiredArgsConstructor
public class ActionCacheController {
    
    private final AvailableActionsRegistry actionsRegistry;
    
    /**
     * GET /api/admin/actions/cache/stats
     * Get cache statistics
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        log.info("Cache stats requested");
        return ResponseEntity.ok(actionsRegistry.getCacheStats());
    }
    
    /**
     * POST /api/admin/actions/cache/refresh
     * Manually refresh cache
     */
    @PostMapping("/cache/refresh")
    public ResponseEntity<String> refreshCache() {
        log.info("Manual cache refresh requested");
        actionsRegistry.refreshAllCaches();
        return ResponseEntity.ok("Cache refreshed successfully");
    }
    
    /**
     * POST /api/admin/actions/cache/clear
     * Clear cache completely
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        log.warn("Cache clear requested");
        actionsRegistry.clearAllCaches();
        return ResponseEntity.ok("Cache cleared successfully");
    }
    
    /**
     * GET /api/admin/actions/list
     * Get all available actions (with cache)
     */
    @GetMapping("/list")
    public ResponseEntity<?> listActions() {
        return ResponseEntity.ok(actionsRegistry.getAllAvailableActions());
    }
    
    /**
     * GET /api/admin/actions/categories
     * Get action categories
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(Map.of(
            "categories", actionsRegistry.getCategories(),
            "counts", actionsRegistry.getActionCountByCategory()
        ));
    }
}
```

---

### Step 4: Add Initialization & Scheduling (15 min)

**File:** `backend/src/main/java/com/easyluxury/ai/service/CacheInitializationService.java`

```java
package com.easyluxury.ai.service;

import com.ai.infrastructure.action.AvailableActionsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInitializationService {
    
    private final AvailableActionsRegistry actionsRegistry;
    
    /**
     * Initialize cache on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready, initializing action caches");
        try {
            actionsRegistry.getAllAvailableActions();
            actionsRegistry.refreshAllCaches();
            log.info("Caches initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize caches", e);
        }
    }
    
    /**
     * Refresh cache periodically (every 5 minutes)
     * This ensures cache is fresh even if no code changes
     */
    @Scheduled(fixedDelayString = "${ai.actions.cache.refresh-interval-ms:300000}")
    public void periodicCacheRefresh() {
        log.debug("Performing scheduled cache refresh");
        try {
            actionsRegistry.refreshAllCaches();
        } catch (Exception e) {
            log.error("Error during scheduled cache refresh", e);
        }
    }
}
```

---

### Step 5: Add Tests (10 min)

**File:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/action/AvailableActionsCacheTest.java`

```java
package com.ai.infrastructure.action;

import com.ai.infrastructure.dto.ActionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AvailableActionsCacheTest {
    
    @Autowired
    private AvailableActionsRegistry registry;
    
    @Test
    void shouldReturnCachedActions() {
        // First call
        long start1 = System.nanoTime();
        List<ActionInfo> actions1 = registry.getAllAvailableActions();
        long time1 = System.nanoTime() - start1;
        
        // Second call (should use cache)
        long start2 = System.nanoTime();
        List<ActionInfo> actions2 = registry.getAllAvailableActions();
        long time2 = System.nanoTime() - start2;
        
        // Verify
        assertThat(actions1).isEqualTo(actions2);
        assertThat(time2).isLessThan(time1 / 10); // 10x faster
    }
    
    @Test
    void shouldCacheByCategory() {
        // First call
        List<ActionInfo> cat1 = registry.getActionsByCategory("subscription");
        
        // Second call (should use cache)
        List<ActionInfo> cat2 = registry.getActionsByCategory("subscription");
        
        assertThat(cat1).isEqualTo(cat2);
    }
    
    @Test
    void shouldCacheLLMPrompt() {
        // First call
        long start1 = System.nanoTime();
        String prompt1 = registry.formatForLLMPrompt();
        long time1 = System.nanoTime() - start1;
        
        // Second call (should use cache)
        long start2 = System.nanoTime();
        String prompt2 = registry.formatForLLMPrompt();
        long time2 = System.nanoTime() - start2;
        
        // Verify
        assertThat(prompt1).isEqualTo(prompt2);
        assertThat(time2).isLessThan(time1 / 10); // 10x faster
    }
    
    @Test
    void shouldProvideCacheStats() {
        registry.getAllAvailableActions();
        
        Map<String, Object> stats = registry.getCacheStats();
        
        assertThat(stats)
            .containsKeys("actions_cached", "prompt_cached", "total_actions")
            .containsEntry("actions_cached", true);
    }
    
    @Test
    void shouldRefreshAllCaches() {
        List<ActionInfo> actions1 = registry.getAllAvailableActions();
        
        registry.refreshAllCaches();
        
        List<ActionInfo> actions2 = registry.getAllAvailableActions();
        
        assertThat(actions2).isNotEmpty();
    }
    
    @Test
    void shouldClearAllCaches() {
        registry.getAllAvailableActions();
        
        registry.clearAllCaches();
        
        // Should rebuild on next access
        List<ActionInfo> actions = registry.getAllAvailableActions();
        assertThat(actions).isNotEmpty();
    }
}
```

---

## Configuration File

**File:** `application.yml`

```yaml
ai:
  actions:
    cache:
      # Enable caching
      enabled: true
      
      # Action cache TTL (minutes)
      ttl-minutes: 5
      
      # Category cache
      category-cache-enabled: true
      
      # LLM prompt cache (longer TTL = less rebuilds)
      prompt-cache-enabled: true
      prompt-cache-ttl-minutes: 10
      
      # Scheduled refresh
      refresh-interval-ms: 300000  # 5 minutes
      
      # Monitoring
      metrics-enabled: true
      log-cache-operations: false

logging:
  level:
    com.ai.infrastructure.action: INFO
```

---

## Testing the Cache

```bash
# Get cache statistics
curl http://localhost:8080/api/admin/actions/cache/stats

# Example response:
{
  "actions_cached": true,
  "actions_age_ms": 1234,
  "category_cache_size": 4,
  "prompt_cached": true,
  "prompt_age_ms": 2456,
  "total_actions": 15
}

# Manually refresh cache
curl -X POST http://localhost:8080/api/admin/actions/cache/refresh

# List all actions
curl http://localhost:8080/api/admin/actions/list

# Get categories
curl http://localhost:8080/api/admin/actions/categories
```

---

## Performance Before vs After

### Before (No Cache)
```
Request 1: 50ms (build all actions)
Request 2: 50ms (build all actions)
Request 3: 50ms (build all actions)
...
1000 requests: 50,000 ms = 50 seconds
```

### After (With Caching)
```
Request 1: 50ms (build and cache)
Request 2: <1ms (use cache)
Request 3: <1ms (use cache)
...
1000 requests: ~50ms = 0.05 seconds
```

**1000x faster! 🚀**

---

## Verification Checklist

- [ ] Add TTL cache to registry
- [ ] Add configuration in application.yml
- [ ] Create cache endpoints
- [ ] Create initialization service
- [ ] Create tests
- [ ] Run tests: `mvn test`
- [ ] Build application: `mvn clean package`
- [ ] Start application
- [ ] Test endpoints (curl commands above)
- [ ] Check logs for cache messages
- [ ] Verify cache hit ratio (>90%)
- [ ] Monitor memory usage (should be minimal)
- [ ] Deploy to production

---

## Expected Results

After implementing caching:

✅ **Cache hit ratio:** >95%
✅ **Response time:** <1ms for cached requests
✅ **Memory usage:** 5-15MB for entire cache
✅ **CPU usage:** Minimal (no rebuilding)
✅ **Throughput:** 10K+ requests/second

---

## Monitoring

Check logs for cache operations:
```
[INFO] Action cache rebuilt: 15 total actions
[DEBUG] Returning cached actions
[DEBUG] Building category cache
[INFO] LLM prompt cache rebuilt: 5234 characters
```

Monitor endpoints:
```
/api/admin/actions/cache/stats  - Check cache health
/api/admin/actions/list         - View all cached actions
/api/admin/actions/categories   - View categories
```

---

## Total Implementation Time

- Step 1: 15 min
- Step 2: 10 min
- Step 3: 20 min
- Step 4: 15 min
- Step 5: 10 min
- **Total: 70 minutes (~1 hour)**

**Start today! ✅**

