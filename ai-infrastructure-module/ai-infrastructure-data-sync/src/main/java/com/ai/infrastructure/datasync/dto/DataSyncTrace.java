package com.ai.infrastructure.datasync.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Trace context for data sync requests.
 *
 * <p>Used for access control, correlation, and audit logging. Values should be stable identifiers
 * (avoid PII such as email/phone).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}

