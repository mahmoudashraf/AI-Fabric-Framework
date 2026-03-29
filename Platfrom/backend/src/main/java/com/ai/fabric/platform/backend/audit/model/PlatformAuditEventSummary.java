package com.ai.fabric.platform.backend.audit.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record PlatformAuditEventSummary(
    String id,
    String actorId,
    String actorRole,
    String action,
    String targetType,
    String targetId,
    JsonNode details,
    Instant createdAt
) {
}
