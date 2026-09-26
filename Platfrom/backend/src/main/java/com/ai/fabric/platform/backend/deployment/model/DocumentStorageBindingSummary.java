package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DocumentStorageBindingSummary(
    String bindingRef,
    String deploymentId,
    String targetProfileId,
    String connectorType,
    String status,
    boolean credentialsPresent,
    JsonNode safeMetadata,
    Instant updatedAt
) {
}
