package com.ai.infrastructure.intent.orchestration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Context for orchestration requests.
 * Supports authenticated (userId) and anonymous (sessionId) flows.
 * At least one identifier (userId or sessionId) must be present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationContext {

    /**
     * User ID for authenticated users.
     */
    private String userId;

    /**
     * Session ID for tracking anonymous usage.
     */
    private String sessionId;

    /**
     * Request ID for tracing; generated if not provided.
     */
    private String requestId;

    /**
     * Client IP address.
     */
    private String ipAddress;

    /**
     * User agent string.
     */
    private String userAgent;

    /**
     * User locale for i18n handling.
     */
    private Locale locale;

    /**
     * Additional metadata (e.g., tier, device, referrer).
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * True if an authenticated userId was provided.
     */
    public boolean isAuthenticated() {
        return userId != null && !userId.isBlank();
    }

    /**
     * True when no authenticated user is present.
     */
    public boolean isAnonymous() {
        return !isAuthenticated();
    }

    /**
     * Primary identifier (userId for authenticated, sessionId otherwise).
     */
    public String getIdentifier() {
        return isAuthenticated() ? userId : sessionId;
    }

    /**
     * Generate requestId if missing; stable prefix aids tracing.
     */
    public String getOrGenerateRequestId() {
        if (requestId == null || requestId.isBlank()) {
            requestId = "rag-" + UUID.randomUUID();
        }
        return requestId;
    }

    /**
     * Validate presence of at least one identifier.
     */
    public void validate() {
        if (!isAuthenticated() && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException(
                "OrchestrationContext must include userId (authenticated) or sessionId (anonymous)"
            );
        }
    }

    // Factory helpers

    public static OrchestrationContext forUser(String userId) {
        return OrchestrationContext.builder().userId(userId).build();
    }

    public static OrchestrationContext forSession(String sessionId) {
        return OrchestrationContext.builder().sessionId(sessionId).build();
    }

    public static OrchestrationContext anonymous() {
        return OrchestrationContext.builder()
            .sessionId("anon-" + UUID.randomUUID())
            .build();
    }

    public static OrchestrationContext forTest() {
        return OrchestrationContext.builder()
            .userId("test-user-" + UUID.randomUUID())
            .sessionId("test-session-" + UUID.randomUUID())
            .build();
    }
}
