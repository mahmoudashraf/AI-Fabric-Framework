package com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;

import java.time.Instant;
import java.util.List;

/** Native effects only. Authorization, durable intents and workflow policy belong to the caller. */
public interface NativeLifecycleAdapter {
    NativeSnapshot freeze(String deploymentId, String targetProfileId, String releaseId);
    Observation observe(NativeSnapshot snapshot);
    Observation stop(NativeSnapshot snapshot, Checkpoint checkpoint);
    /** Worker-only proof. Complete never asserts that the whole snapshot is stopped or reclaimed. */
    Observation quiesceWorkers(NativeSnapshot snapshot, Checkpoint checkpoint);
    boolean supportsRemoteDependency(NativeSnapshot snapshot, String kind);
    /** Starts the frozen, already-existing resources after the caller's stop phase; never provisions. */
    Observation restart(NativeSnapshot snapshot, Checkpoint checkpoint);
    Observation delete(NativeSnapshot snapshot, boolean reclaimOwnedData, Checkpoint checkpoint);
    Observation verifyStopped(NativeSnapshot snapshot);
    Observation verifyAbsent(NativeSnapshot snapshot);

    interface Checkpoint {
        /** Commit intent before dispatch. False forbids redispatch, including an unknown outcome. */
        boolean beforeEffect(String effectId);
        /** Call only after the exact intended effect has been read back. */
        void completed(String effectId);
        boolean isComplete(String effectId);
        default void dispatched(String effectId, String providerOperationId) { }
        default String operationId(String effectId) { return null; }
        void guard();
    }

    enum Origin { HANDLE, DEPENDENCY }
    enum State { RUNNING, STOPPED, ABSENT, TRANSITIONING, UNKNOWN }

    record Placement(String controlPlane, String projectUuid, String environmentUuid,
                     String serverUuid, String destinationUuid, TargetIntent targetIntent) { }
    record HandleIdentity(String releaseId, Instant createdAt, String metadataHash, String ownershipReceiptId) { }
    record DependencyIdentity(String graphId, String slot, String serviceRef) { }
    record Volume(String uuid, String type, String name, String mountPath, String hostPath, boolean retained) { }
    record NativeResource(String id, Origin origin, String kind, String uuid, String name, String marker,
                          String identityHash, Placement placement, HandleIdentity handle,
                          DependencyIdentity dependency, List<Volume> volumes) {
        public NativeResource { volumes = volumes == null ? List.of() : List.copyOf(volumes); }
        public boolean database() { return "RUNTIME_POSTGRES_DATABASE".equals(kind); }
    }
    record GraphIdentity(String id, String versionId, String releaseId, String specFingerprint,
                         String bindingFingerprint, String resourceFingerprint) { }
    record Exclusion(String id, String kind, String referenceFingerprint) { }
    record NativeSnapshot(String customerId, String tenantId, String deploymentId, String targetProfileId,
                          String releaseId, String targetFingerprint, String releaseFingerprint,
                          List<NativeResource> resources, List<GraphIdentity> graphs,
                          List<Exclusion> exclusions, String scopeHash, Instant capturedAt) {
        public NativeSnapshot {
            resources = resources == null ? List.of() : List.copyOf(resources);
            graphs = graphs == null ? List.of() : List.copyOf(graphs);
            exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        }
    }
    record ResourceObservation(String resourceId, State state, String providerStatus, boolean containersAbsent,
                               boolean volumesAbsent, List<String> blockers) {
        public ResourceObservation { blockers = blockers == null ? List.of() : List.copyOf(blockers); }
    }
    record Observation(String scopeHash, Instant observedAt, List<ResourceObservation> resources,
                       boolean stopped, boolean absent, boolean ready, boolean dataReclaimed,
                       boolean complete, List<String> blockers) {
        public Observation {
            resources = resources == null ? List.of() : List.copyOf(resources);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
