package com.ai.infrastructure.access;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIAccessSubjectContext;
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
        AIAccessSubjectContext authContext = requireAuthContext(request);
        String subjectId = resolveSubjectId(authContext);

        LocalDateTime evaluationTimestamp = Optional.ofNullable(request.getTimestamp())
            .orElseGet(() -> LocalDateTime.now(clock));
        Map<String, Object> entityContext = buildEntityContext(request, evaluationTimestamp);

        Decision decision = evaluateAccess(policy, authContext, entityContext);
        if (!decision.granted()) {
            logDenied(policy, authContext, entityContext);
        }

        long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        return AIAccessControlResponse.builder()
            .requestId(request.getRequestId())
            .subjectId(subjectId)
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

    private AIAccessSubjectContext requireAuthContext(AIAccessControlRequest request) {
        AIAccessSubjectContext authContext = request.getAuthContext();
        if (authContext != null && (hasText(authContext.getSubjectId()) || hasText(authContext.getSessionId()))) {
            return authContext;
        }
        throw new IllegalArgumentException("authContext.subjectId or authContext.sessionId must be provided");
    }

    private String resolveSubjectId(AIAccessSubjectContext authContext) {
        if (hasText(authContext.getSubjectId())) {
            return authContext.getSubjectId().trim();
        }
        return authContext.getSessionId().trim();
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
            // Filter out null values before copying to immutable map (Map.copyOf doesn't accept nulls)
            Map<String, Object> filteredMetadata = request.getMetadata().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!filteredMetadata.isEmpty()) {
                context.put("metadata", Map.copyOf(filteredMetadata));
            }
        }
        if (request.getUserAttributes() != null && !request.getUserAttributes().isEmpty()) {
            // Filter out null values before copying to immutable map (Map.copyOf doesn't accept nulls)
            Map<String, Object> filteredAttributes = request.getUserAttributes().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!filteredAttributes.isEmpty()) {
                context.put("userAttributes", Map.copyOf(filteredAttributes));
            }
        }
        if (request.getAuthContext() != null) {
            context.put("authContext", request.getAuthContext());
        }
        return context;
    }

    private Decision evaluateAccess(EntityAccessPolicy policy, AIAccessSubjectContext authContext, Map<String, Object> entityContext) {
        try {
            boolean granted = policy.canAccess(authContext, Collections.unmodifiableMap(entityContext));
            return new Decision(granted, false, null);
        } catch (Exception ex) {
            log.warn("EntityAccessPolicy threw an exception for subject {}: {}", resolveSubjectId(authContext), ex.getMessage());
            return new Decision(false, true, ex.getMessage());
        }
    }

    private void logDenied(EntityAccessPolicy policy, AIAccessSubjectContext authContext, Map<String, Object> entityContext) {
        try {
            policy.logAccessDenied(authContext, Collections.unmodifiableMap(entityContext), "POLICY_DENIED");
        } catch (Exception ex) {
            log.debug("EntityAccessPolicy.logAccessDenied failed: {}", ex.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Decision(boolean granted, boolean hookFailed, String errorMessage) { }
}
