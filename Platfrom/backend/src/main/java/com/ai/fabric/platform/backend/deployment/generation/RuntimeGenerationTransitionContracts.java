package com.ai.fabric.platform.backend.deployment.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.time.Instant;
import java.util.List;

public final class RuntimeGenerationTransitionContracts {
    private RuntimeGenerationTransitionContracts() { }

    public enum State { ACTIVE, DRAINING, AUTHORITY_REVOKED, RECLAIM_STARTED }
    public enum CommandType {
        BEGIN_DRAIN, CANCEL_DRAIN, FENCE_AUTHORITY,
        RECORD_AUTHORITY_REVOCATION, RECORD_RECLAIM_STARTED
    }

    public record Target(String deploymentId, String targetProfileId, String releaseId) { }
    public record Binding(Target target, long fenceEpoch, String operationId, String requestFingerprint) { }
    public record Command(Binding binding, long expectedFenceEpoch, CommandType type,
                          Instant drainDeadline, String externalEvidenceReference) { }
    public record Readback(Target target, String tenantId, State state,
                           @JsonProperty(value = "fenceEpoch", required = true) @JsonSetter(nulls = Nulls.FAIL) long fenceEpoch,
                           String operationId, String requestFingerprint, Instant drainDeadline,
                           boolean authorityRevoked, boolean reclaimStarted, Instant updatedAt) { }
    public record CommandResult(Readback applied, Readback current, boolean replay) { }
    public record Observation(String source, Long observedCount, boolean complete, List<String> blockers) { }
    public record Metric(Long count, boolean complete, List<Observation> observations) { }
    public record RemoteObligation(String dependencyKind, Target target, String topologyFingerprint,
                                   String requirement) { }
    public record Snapshot(String snapshotId, String evidenceReference, Readback target,
                           Instant observedAt, Metric openConversations, Metric pendingConfirmations,
                           Metric humanWork, Metric backgroundWork, long inFlightRequests,
                           boolean inventoryComplete, boolean workerFencingComplete,
                           boolean localDrainComplete, boolean reclaimable, boolean deadlineElapsed,
                           String topologyFingerprint, List<RemoteObligation> remoteObligations,
                           List<String> blockers) { }
    public record GenerationTransition(Target previousTarget, Target nextTarget, String tenantId,
                                       long expectedFenceEpoch, String previousTopologyFingerprint,
                                       String nextTopologyFingerprint, String operationId,
                                       String requestFingerprint, String providerStopEvidenceReference) { }
    public record GenerationTransitionResult(Readback previous, Readback current,
                                             String providerStopEvidenceReference,
                                             Instant transitionedAt, boolean replay) { }
    public record ColdMaintenanceTransition(GenerationTransition transition,
                                             String previousOperationId,
                                             String previousRequestFingerprint,
                                             String approvalReference,
                                             boolean acceptInMemoryStateLoss) { }
    public record ColdMaintenanceTransitionResult(GenerationTransitionResult transition,
                                                   Snapshot observedInventory,
                                                   String evidenceKind,
                                                   String approvalReference,
                                                   boolean inMemoryStateLossAcknowledged) { }
}
