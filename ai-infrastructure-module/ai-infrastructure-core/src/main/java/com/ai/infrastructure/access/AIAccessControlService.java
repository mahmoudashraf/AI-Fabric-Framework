package com.ai.infrastructure.access;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Infrastructure-only access control service that delegates policy decisions to customer supplied hooks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAccessControlService {

    private static final String CACHE_NAME = "accessDecisions";
    private static final int MAX_HISTORY = 1_000;

    private final AuditService auditService;
    private final CacheManager cacheManager;
    private final Clock clock;

    private final Map<String, List<AIAccessControlRequest>> accessHistory = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userCacheKeys = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private EntityAccessPolicy entityAccessPolicy;

    public AIAccessControlResponse checkAccess(AIAccessControlRequest request) {
        long started = System.nanoTime();
        try {
            validateRequest(request);

            LocalDateTime evaluationTimestamp = Optional.ofNullable(request.getTimestamp())
                .orElseGet(() -> LocalDateTime.now(clock));
            Map<String, Object> entityContext = buildEntityContext(request, evaluationTimestamp);

            Cache cache = resolveCache();
            String cacheKey = generateCacheKey(request.getUserId(), entityContext);
            Boolean cachedDecision = getCachedDecision(cache, cacheKey);

            boolean accessGranted = cachedDecision != null
                ? cachedDecision
                : evaluateAccess(request.getUserId(), entityContext);

            if (cache != null && cachedDecision == null) {
                cache.put(cacheKey, accessGranted);
                trackCacheKey(request.getUserId(), cacheKey);
            }

            logAccessAttempt(request, accessGranted, evaluationTimestamp, entityContext);

            long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return AIAccessControlResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .resourceId((String) entityContext.get("resourceId"))
                .operationType((String) entityContext.get("operationType"))
                .accessGranted(accessGranted)
                .fromCache(cachedDecision != null)
                .accessDecision(accessGranted ? "GRANT" : "DENY")
                .processingTimeMs(durationMs)
                .timestamp(evaluationTimestamp)
                .success(true)
                .build();
        } catch (Exception ex) {
            log.error("Access control check failed", ex);
            auditService.logOperation(
                request != null ? request.getRequestId() : null,
                request != null ? request.getUserId() : null,
                "ACCESS_ERROR",
                List.of(ex.getMessage()));
            return AIAccessControlResponse.builder()
                .requestId(request != null ? request.getRequestId() : null)
                .userId(request != null ? request.getUserId() : null)
                .accessGranted(false)
                .fromCache(false)
                .success(false)
                .errorMessage(ex.getMessage())
                .build();
        }
    }

    public List<AIAccessControlRequest> getAccessHistory(String userId) {
        return accessHistory.containsKey(userId)
            ? List.copyOf(accessHistory.get(userId))
            : List.of();
    }

    public void invalidateUserCache(String userId) {
        Cache cache = resolveCache();
        if (cache == null) {
            return;
        }
        Set<String> keys = userCacheKeys.remove(userId);
        if (keys != null) {
            keys.forEach(cache::evict);
        }
    }

    private void validateRequest(AIAccessControlRequest request) {
        Objects.requireNonNull(request, "access request must not be null");
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId must be provided");
        }
    }

    private Map<String, Object> buildEntityContext(AIAccessControlRequest request, LocalDateTime timestamp) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("resourceId", Optional.ofNullable(request.getResourceId()).orElse("UNKNOWN"));
        context.put("operationType", Optional.ofNullable(request.getOperationType()).orElse("READ"));
        context.put("timestamp", timestamp);
        context.put("ipAddress", request.getIpAddress());
        context.put("purpose", request.getPurpose());
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            context.put("metadata", Map.copyOf(request.getMetadata()));
        }
        if (request.getUserAttributes() != null && !request.getUserAttributes().isEmpty()) {
            context.put("userAttributes", Map.copyOf(request.getUserAttributes()));
        }
        return context;
    }

    private boolean evaluateAccess(String userId, Map<String, Object> entity) {
        if (entityAccessPolicy == null) {
            return true;
        }
        return entityAccessPolicy.canUserAccessEntity(userId, Collections.unmodifiableMap(entity));
    }

    private Cache resolveCache() {
        return cacheManager != null ? cacheManager.getCache(CACHE_NAME) : null;
    }

    private Boolean getCachedDecision(Cache cache, String cacheKey) {
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private void trackCacheKey(String userId, String cacheKey) {
        userCacheKeys
            .computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>())
            .add(cacheKey);
    }

    private String generateCacheKey(String userId, Map<String, Object> entity) {
        String resourceId = Objects.toString(entity.get("resourceId"), "UNKNOWN");
        String operationType = Objects.toString(entity.get("operationType"), "READ");
        return String.join(":", "access", userId, operationType, resourceId);
    }

    private void logAccessAttempt(AIAccessControlRequest request,
                                  boolean granted,
                                  LocalDateTime timestamp,
                                  Map<String, Object> entityContext) {
        accessHistory.computeIfAbsent(request.getUserId(),
                key -> Collections.synchronizedList(new ArrayList<>()))
            .add(ensureTimestamp(request, timestamp));

        List<AIAccessControlRequest> history = accessHistory.get(request.getUserId());
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }

        auditService.logOperation(
            request.getRequestId(),
            request.getUserId(),
            granted ? "ACCESS_GRANTED" : "ACCESS_DENIED",
            List.of(Objects.toString(entityContext.get("resourceId"), "UNKNOWN"),
                Objects.toString(entityContext.get("operationType"), "READ")),
            timestamp);

        if (!granted && entityAccessPolicy != null) {
            entityAccessPolicy.logAccessDenied(
                request.getUserId(),
                Collections.unmodifiableMap(entityContext),
                "POLICY_DENIED");
        }
    }

    private AIAccessControlRequest ensureTimestamp(AIAccessControlRequest request, LocalDateTime timestamp) {
        if (request.getTimestamp() == null) {
            request.setTimestamp(timestamp);
        }
        return request;
    }
}
