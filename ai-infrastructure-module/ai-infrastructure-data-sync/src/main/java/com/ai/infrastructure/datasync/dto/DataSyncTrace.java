package com.ai.infrastructure.datasync.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Trace context for data sync requests.
 *
 * <p>Used for access control, correlation, and audit logging. Values should be stable identifiers
 * (avoid PII such as email/phone).</p>
 */
public class DataSyncTrace {

    /**
     * Stable identifier of the actor performing the sync (service identity or user id).
     */
    @NotBlank
    private String userId;

    /**
     * Optional session identifier.
     */
    private String sessionId;

    /**
     * Optional request identifier. If absent, the server may generate one for logging.
     */
    private String requestId;

    /**
     * Optional trace metadata (PII-safe).
     */
    private Map<String, Object> metadata;

    public DataSyncTrace() {
    }

    public DataSyncTrace(String userId, String sessionId, String requestId, Map<String, Object> metadata) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.metadata = metadata;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
