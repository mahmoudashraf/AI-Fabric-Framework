package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record DeploymentBehaviorReadinessSummary(
    String id,
    String schemaVersion,
    String behaviorType,
    String templatePluginId,
    String templatePluginVersionId,
    String templatePluginVersion,
    String compositionHash,
    String materialHash,
    String frameworkVersion,
    String sourceArtifactId,
    String sourceCommit,
    String imageDigest,
    String sourceCapabilityManifestHash,
    List<String> verificationPackIds,
    String maturity,
    String effectiveMaturity,
    String status,
    boolean expired,
    JsonNode hostedProofs,
    JsonNode approvalEvidence,
    String deploymentId,
    String deploymentVersionId,
    String releaseId,
    Instant evaluatedAt,
    Instant expiresAt,
    String approvedByActorId,
    Instant approvedAt,
    String approvalNote,
    Instant createdAt,
    Instant updatedAt
) {
}
