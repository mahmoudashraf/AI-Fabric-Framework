package com.ai.infrastructure.access;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Minimal infrastructure access control service: validate request, delegate to customer hook,
 * and fail closed when hooks are unavailable.
 */
@Slf4j
@RequiredArgsConstructor
public class AIAccessControlService {

    private final Clock clock;
    private final EntityAccessPolicy entityAccessPolicy;

    public AIAccessControlResponse checkAccess(AIAccessControlRequest request) {
        long started = System.nanoTime();
        Objects.requireNonNull(request, "access request must not be null");

        EntityAccessPolicy policy = requirePolicy();
        String userId = extractUserId(request);

        LocalDateTime evaluationTimestamp = Optional.ofNullable(request.getTimestamp())
            .orElseGet(() -> LocalDateTime.now(clock));
        Map<String, Object> entityContext = buildEntityContext(request, evaluationTimestamp);

        Decision decision = evaluateAccess(policy, userId, entityContext);
        if (!decision.granted()) {
            logDenied(policy, userId, entityContext);
        }

        long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        return AIAccessControlResponse.builder()
            .requestId(request.getRequestId())
            .userId(userId)
            .resourceId(Objects.toString(entityContext.get("resourceId"), null))
            .operationType(Objects.toString(entityContext.get("operationType"), null))
            .accessGranted(decision.granted())
            .fromCache(Boolean.FALSE)
            .accessDecision(decision.granted() ? "GRANT" : "DENY")
            .processingTimeMs(durationMs)
            .timestamp(evaluationTimestamp)
            .success(!decision.hookFailed())
            .errorMessage(decision.hookFailed() ? decision.errorMessage() : null)
            .build();
    }

    private EntityAccessPolicy requirePolicy() {
        if (entityAccessPolicy == null) {
            throw new IllegalStateException("""
                No EntityAccessPolicy bean available. Register a bean implementing \
                com.ai.infrastructure.access.policy.EntityAccessPolicy to evaluate access decisions.""");
        }
        return entityAccessPolicy;
    }

    private String extractUserId(AIAccessControlRequest request) {
        String userId = request.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must be provided");
        }
        return userId;
    }

    private Map<String, Object> buildEntityContext(AIAccessControlRequest request, LocalDateTime timestamp) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("resourceId", Optional.ofNullable(request.getResourceId()).orElse("UNKNOWN"));
        context.put("operationType", Optional.ofNullable(request.getOperationType()).orElse("READ"));
        context.put("timestamp", timestamp);
        if (request.getContext() != null) {
            context.put("context", request.getContext());
        }
        if (request.getPurpose() != null) {
            context.put("purpose", request.getPurpose());
        }
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            context.put("metadata", Map.copyOf(request.getMetadata()));
        }
        if (request.getUserAttributes() != null && !request.getUserAttributes().isEmpty()) {
            context.put("userAttributes", Map.copyOf(request.getUserAttributes()));
        }
        return context;
    }

    private Decision evaluateAccess(EntityAccessPolicy policy, String userId, Map<String, Object> entityContext) {
        try {
            boolean granted = policy.canUserAccessEntity(userId, Collections.unmodifiableMap(entityContext));
            return new Decision(granted, false, null);
        } catch (Exception ex) {
            log.warn("EntityAccessPolicy threw an exception for user {}: {}", userId, ex.getMessage());
            return new Decision(false, true, ex.getMessage());
        }
    }

    private void logDenied(EntityAccessPolicy policy, String userId, Map<String, Object> entityContext) {
        try {
            policy.logAccessDenied(userId, Collections.unmodifiableMap(entityContext), "POLICY_DENIED");
        } catch (Exception ex) {
            log.debug("EntityAccessPolicy.logAccessDenied failed: {}", ex.getMessage());
        }
    }

    private record Decision(boolean granted, boolean hookFailed, String errorMessage) { }
}
