package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.UnusedConsumerDetachment;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.UnusedConsumerDetachmentReceipt;
import com.fasterxml.jackson.annotation.JsonInclude;

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
                                 Scope destination, List<Source> sources, InventoryApproval inventoryApproval,
                                 @JsonInclude(JsonInclude.Include.NON_EMPTY) List<UnusedConsumerDetachment> consumerDetachments) {
        public ReleaseRequest {
            if (sources != null) sources = List.copyOf(sources);
            consumerDetachments = consumerDetachments == null ? List.of() : List.copyOf(consumerDetachments);
        }
        public ReleaseRequest(String operationKey, String approvalReference, String bindingId,
                              Scope destination, List<Source> sources, InventoryApproval inventoryApproval) {
            this(operationKey, approvalReference, bindingId, destination, sources, inventoryApproval, List.of());
        }
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
                                 List<CredentialClearance> credentialClearances,
                                 @JsonInclude(JsonInclude.Include.NON_EMPTY) List<UnusedConsumerDetachmentReceipt> consumerDetachments,
                                 ClusterReadback emptyClusterReadback, String approvalReference,
                                 String actorId, Instant releasedAt, String fingerprint) {
        public ReleaseReceipt {
            sources = List.copyOf(sources);
            credentialClearances = List.copyOf(credentialClearances);
            consumerDetachments = consumerDetachments == null ? List.of() : List.copyOf(consumerDetachments);
        }
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
