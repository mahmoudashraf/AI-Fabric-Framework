package com.ai.fabric.platform.backend.deployment.retirement;

import java.time.Instant;

/** Preservation authorizes no provider mutation. Bytes are a reviewed envelope, not a measurement. */
public final class ExternalIndexRetentionContracts {
    private ExternalIndexRetentionContracts() { }

    public record Binding(String bindingId, String metadataFingerprint, String clusterId, String endpointHost,
                          String databaseName, String provider, String scopeType, String scopeValue) { }
    /** The acceptance deadline is checked once; accepted preservation is durable for this immutable scope. */
    public record Policy(String reservationId, String generation, String manifestFingerprint,
                         String bindingFingerprint, Binding binding, long indexes, long indexBytes,
                         String policyReference, String payerReference, Instant reviewAcceptBefore) { }
    public record Authorization(String reservationId, String policyFingerprint) { }
    public record Receipt(String id, String operationId, String operationKey, String requestFingerprint,
                          long fenceEpoch, String snapshotFingerprint, String reservationId, String generation,
                          String policyFingerprint, Binding binding, long indexes, long indexBytes,
                          TargetRetirementRuntimeIndexGateway.IndexReadback indexReadback,
                          com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Snapshot runtimeSnapshot,
                          Instant recordedAt) { }
}
