package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TargetRetirementContracts {
    private TargetRetirementContracts() { }

    public enum State { PLANNED, QUEUED, RUNNING, WAITING, RETAINED, BLOCKED, RETRYABLE_FAILURE, CANCEL_REQUESTED, CANCELLED, COMPLETE }
    public enum Step { DRAIN, AUTHORITY, INDEX, RESOURCES, DEPENDENCIES, VERIFY, CANCEL_DRAIN }
    public enum ParticipantKind { RUNTIME_DRAIN, AUTHORITY, INDEX, DEPENDENCIES }
    public enum RetentionDecision { RECLAIM, RETAIN_UNTIL }

    public record ConsumerExpectation(String consumerEntityId, long expectedBindingRevision) { }
    public record Retention(RetentionDecision decision, Instant expiresAt, String entitlementReference,
                            String backupDecisionReference) { }
    public record PlanRequest(String operationKey, List<String> releaseIds,
                              List<ConsumerExpectation> consumers, Instant drainDeadline,
                              Retention retention, String reason) { }
    public record Command(String requestFingerprint) { }
    public record ReleaseGeneration(String releaseId, String versionId, String generation) { }
    public record ProviderVolumeGeneration(String storageUuid, String type, String name, String mountPath,
                                           String hostPath, String generation, boolean preserved) { }
    public record ResourceGeneration(String handleId, String releaseId, DeploymentProviderType providerType,
                                     String resourceKind, String providerResourceUuid, String serverUuid,
                                     String projectUuid, String environmentUuid, String destinationUuid,
                                     String providerName, String deploymentMarker,
                                     String generation, String providerIdentityFingerprint,
                                     List<ProviderVolumeGeneration> volumes, boolean preserved) {
        public ResourceGeneration {
            volumes = volumes == null ? List.of() : List.copyOf(volumes);
        }
        public ResourceGeneration(String handleId, String releaseId, DeploymentProviderType providerType,
                                  String resourceKind, String providerResourceUuid, String serverUuid,
                                  String generation, boolean preserved) {
            this(handleId, releaseId, providerType, resourceKind, providerResourceUuid, serverUuid,
                null, null, null, null, null, generation, null, List.of(), preserved);
        }
        public ResourceGeneration(String handleId, String releaseId, DeploymentProviderType providerType,
                                  String resourceKind, String providerResourceUuid, String serverUuid,
                                  String generation, List<ProviderVolumeGeneration> volumes, boolean preserved) {
            this(handleId, releaseId, providerType, resourceKind, providerResourceUuid, serverUuid,
                null, null, null, null, null, generation, null, volumes, preserved);
        }
    }
    public record Snapshot(String customerId, String tenantId, String deploymentId, String targetProfileId,
                           String targetGeneration, long targetEpoch, List<ReleaseGeneration> releases,
                           List<ResourceGeneration> resources, List<ConsumerExpectation> consumers) { }
    public record Context(String operationId, String operationKey, String requestFingerprint, Snapshot snapshot, long fenceEpoch,
                          String workerToken, String requestedByActorId, String requestedByRole,
                          Instant drainDeadline, Retention retention) { }

    /** Evidence references must point to durable, target-bound records, not health or UI observations. */
    public record Evidence(String requestFingerprint, long fenceEpoch, Instant observedAt, String reference,
                           boolean complete, boolean ingressBlocked, boolean inventoryComplete,
                           boolean retentionApproved, Long conversations, Long confirmations,
                           Long humanWork, Long backgroundWork, boolean durable, List<String> blockers) { }
    public record RecoveryEvidence(String operationId, String previousWorkerToken, String effectKey,
                                   Instant observedAt, String reference, boolean workerFenced,
                                   boolean remoteOutcomeSettled, boolean resumeSafe) { }
    public record EffectResult(boolean complete, String evidenceReference, Instant observedAt) { }
    public record Progress(Map<String, EffectResult> effects, Map<ParticipantKind, Evidence> evidence,
                           Evidence interlockEvidence, List<String> blockers) {
        public Progress(Map<String, EffectResult> effects, List<String> blockers) { this(effects, Map.of(), null, blockers); }
    }
    public record Summary(String id, String deploymentId, String targetProfileId, String customerId,
                          String operationKey, String requestFingerprint, State state, Step step,
                          Snapshot snapshot, Instant drainDeadline, Retention retention, int attempt,
                          Instant nextRetryAt, String errorCode, Progress result, boolean consumesCapacity,
                          String inFlightEffect, Instant createdAt, Instant startedAt,
                          Instant completedAt, Instant updatedAt) { }
}
