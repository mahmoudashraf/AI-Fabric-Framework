package com.ai.fabric.platform.backend.deployment.operations;

import java.time.Instant;

public final class DeploymentOperationsContracts {
    private DeploymentOperationsContracts() { }

    public enum Decision { ALLOW, DENY, UNAVAILABLE }

    public record ResourceDemand(long cpuMillis, long memoryMiB, long diskMiB, int databases,
                                 int indexes, int dependencies, int replicas, int concurrentApplies) { }
    public record AssessmentRequest(String schemaVersion, String commandId, String customerId,
        String tenantId, String deploymentId, String targetProfileId, String releaseId,
        String versionId, String versionConfigHash, String operation, String mode,
        String requestFingerprint, ResourceDemand resourceDemand, Instant requestedAt) { }
    public record AssessmentResponse(String schemaVersion, String commandId, String requestFingerprint,
        String assessmentId, Decision decision, String reason, String policyRevision,
        Instant observedAt, Instant expiresAt) { }
    public record Outcome(String schemaVersion, String commandId, String requestFingerprint,
                          String status, Instant observedAt) { }
    public record EffectiveDecision(String mode, String status, String reason, String requestFingerprint,
        String assessmentId, String policyRevision, Instant observedAt) { }
    public record Status(String mode, boolean manualDeploymentsAllowed, boolean serviceConfigured,
                         String providerUsageMode, String decisionSource) { }
}
