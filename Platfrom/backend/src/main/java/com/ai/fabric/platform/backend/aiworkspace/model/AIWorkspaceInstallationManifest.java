package com.ai.fabric.platform.backend.aiworkspace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record AIWorkspaceInstallationManifest(
    String schemaVersion,
    String installationId,
    String manifestRevision,
    String assignmentRevision,
    Instant generatedAt,
    long cacheTtlSeconds,
    JsonNode connection,
    JsonNode workspace
) {
}
