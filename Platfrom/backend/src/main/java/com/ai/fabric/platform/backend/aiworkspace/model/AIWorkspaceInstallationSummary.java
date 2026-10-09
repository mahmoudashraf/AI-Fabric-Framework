package com.ai.fabric.platform.backend.aiworkspace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record AIWorkspaceInstallationSummary(
    String id,
    String installationId,
    String customerId,
    String consumerId,
    String displayName,
    String status,
    String experiencePackCode,
    String experiencePackVersion,
    String connectionMode,
    String connectionProfileCode,
    String connectionProfileVersion,
    JsonNode connectionConfiguration,
    List<String> allowedOrigins,
    JsonNode configuration,
    String deploymentId,
    String releaseId,
    String assignmentRevision,
    boolean ready,
    List<AIWorkspaceReadinessCheck> readinessChecks,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt,
    Instant activatedAt,
    Instant disabledAt
) {
}
