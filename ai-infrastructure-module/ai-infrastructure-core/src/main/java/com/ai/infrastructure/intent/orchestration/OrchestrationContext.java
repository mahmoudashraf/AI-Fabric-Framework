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
 * Rich orchestration context supporting both authenticated and anonymous users.
 * Either userId (authenticated) or sessionId (anonymous) must be provided.
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
     * Session ID for anonymous tracking.
     * Required when userId is absent.
     */
    private String sessionId;

    /**
     * Request ID for tracing (auto-generated if missing).
     */
    private String requestId;

    /**
     * Client IP address (internal use only).
     */
    private String ipAddress;

    /**
     * Client user agent (internal use only).
     */
    private String userAgent;

    /**
     * Locale for user responses.
     */
    private Locale locale;

    /**
     * Additional metadata (subscription tier, device type, etc.).
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * True when a userId is present.
     */
    public boolean isAuthenticated() {
        return userId != null && !userId.isBlank();
    }

    /**
     * True when no userId is present.
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
     * Return requestId, generating one if absent.
     */
    public String getOrGenerateRequestId() {
        if (requestId == null || requestId.isBlank()) {
            requestId = "rag-" + UUID.randomUUID();
        }
        return requestId;
    }

    /**
     * Ensure either userId or sessionId is present.
     */
    public void validate() {
        if (!isAuthenticated() && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException("OrchestrationContext requires userId (authenticated) or sessionId (anonymous)");
        }
    }

    /**
     * Authenticated context factory.
     */
    public static OrchestrationContext forUser(String userId) {
        return OrchestrationContext.builder()
            .userId(userId)
            .build();
    }

    /**
     * Anonymous context factory with explicit session.
     */
    public static OrchestrationContext forSession(String sessionId) {
        return OrchestrationContext.builder()
            .sessionId(sessionId)
            .build();
    }

    /**
     * Anonymous context with generated session ID.
     */
    public static OrchestrationContext anonymous() {
        return OrchestrationContext.builder()
            .sessionId("anon-" + UUID.randomUUID())
            .build();
    }

    /**
     * Factory for tests.
     */
    public static OrchestrationContext forTest() {
        return OrchestrationContext.builder()
            .userId("test-user-" + UUID.randomUUID())
            .sessionId("test-session-" + UUID.randomUUID())
            .build();
    }
}
