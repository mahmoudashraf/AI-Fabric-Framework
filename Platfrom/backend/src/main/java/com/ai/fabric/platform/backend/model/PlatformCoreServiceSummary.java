package com.ai.fabric.platform.backend.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record PlatformCoreServiceSummary(
    String serviceRef,
    String displayName,
    String serviceKind,
    String managementMode,
    String targetProfileId,
    String providerResourceUuid,
    String publicBaseUrl,
    String healthPath,
    String healthUrl,
    String status,
    String observedStatus,
    String message,
    Instant observedAt,
    JsonNode details
) {
}
