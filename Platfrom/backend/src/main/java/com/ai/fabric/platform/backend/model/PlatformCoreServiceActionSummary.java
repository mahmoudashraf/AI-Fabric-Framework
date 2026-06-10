package com.ai.fabric.platform.backend.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record PlatformCoreServiceActionSummary(
    String serviceRef,
    String displayName,
    String action,
    String status,
    String message,
    String deploymentUuid,
    String targetProfileId,
    String providerResourceUuid,
    Instant requestedAt,
    JsonNode details
) {
}
