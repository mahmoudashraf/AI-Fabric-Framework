package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Staff-reviewed UNUSED external references only; never provider ownership or credential revocation. */
public final class UnusedVectorTransferContracts {
    private UnusedVectorTransferContracts() { }

    public record ControlPlane(String id, String origin) { }
    public record Scope(ControlPlane controlPlane, String customerId, String tenantId, String deploymentId,
                        String targetProfileId, String versionId, String versionConfigHash, String collectionPrefix) { }
    public record VectorIdentity(String clusterId, String projectId, String regionId, String deploymentOption,
                                 String endpointHost, String databaseName, String providerType) { }
    public record Source(String bindingId, String expectedBindingFingerprint, String cancellationReceiptId) { }
    public record InventoryApproval(String expectedCredentialFingerprint, String fullVisibilityReviewReference,
                                     String noWriterWindowReference) { }
    public record ReleaseRequest(String operationKey, String approvalReference, String bindingId,
                                 Scope destination, List<Source> sources, InventoryApproval inventoryApproval) {
        public ReleaseRequest { if (sources != null) sources = List.copyOf(sources); }
    }
    public record ReleasedBinding(String bindingId, Scope scope, String metadataFingerprint,
                                  String identityFingerprint, String cancellationReceiptId,
                                  String previousStatus, String noEffectFingerprint) { }
    public record CredentialClearance(String bindingId, String authenticationType,
                                      boolean localServingCopyCleared, boolean localFingerprintCopyCleared) { }
    public record ClusterReadback(String endpointHost, Map<String, List<String>> collectionsByDatabase,
                                  String credentialFingerprint, String fullVisibilityReviewReference,
                                  String noWriterWindowReference, String scopeReviewDigest,
                                  Instant observedAt, String fingerprint) { }
    public record ReleaseReceipt(String id, String operationKey, String commandFingerprint, Scope source,
                                 Scope destination, VectorIdentity vector, List<ReleasedBinding> sources,
                                 List<CredentialClearance> credentialClearances, ClusterReadback emptyClusterReadback, String approvalReference,
                                 String actorId, Instant releasedAt, String fingerprint) {
        public ReleaseReceipt { sources = List.copyOf(sources); credentialClearances = List.copyOf(credentialClearances); }
    }
    public record ReleaseReadback(ControlPlane sourceControlPlane, String releaseJson, String releaseJsonDigest,
                                  String readByActorId, String authenticationMode, Instant observedAt, String fingerprint) { }
    public record ReviewRequest(String operationKey, String approvalReference, String authenticatedReadbackJson,
                                 String expectedSourceReceiptFingerprint, String retrievalReference) { }
    public record ReviewReceipt(String id, String operationKey, String approvalReference,
                                String authenticatedReadbackJson, String authenticatedResponseDigest,
                                String sourceReceiptJson, String sourceReceiptFingerprint, String retrievalReference,
                                String reviewerActorId,
                                Instant reviewedAt, String fingerprint) { }
    public record ClaimRequest(String operationKey, String reviewId, ExistingVectorBindingContracts.Request connection,
                               InventoryApproval inventoryApproval) {
        @Override public String toString() { return "UnusedVectorTransferClaim[credentials redacted]"; }
    }
    public record ClaimReceipt(String id, String operationKey, String commandFingerprint, String reviewId,
                               String sourceReceiptId, ControlPlane sourceControlPlane, Scope destination,
                               VectorIdentity vector, ClusterReadback emptyClusterReadback, ExistingVectorBindingContracts.Summary binding,
                               String actorId, Instant claimedAt, String fingerprint) { }
}
