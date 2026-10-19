package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.TreeSet;

/** Control-plane contracts. Quantities are integral in the named units, never browser estimates. */
public final class AdmissionContracts {
    private AdmissionContracts() { }

    public enum Dimension {
        ENVIRONMENTS, REPLICAS, CPU_MILLICORES, MEMORY_BYTES, DISK_BYTES,
        DATABASES, INDEXES, INDEX_BYTES, DEPENDENCIES, CONCURRENT_APPLIES, CONCURRENT_BUILDS,
        API_REQUESTS_PER_WINDOW, PROVIDER_TOKENS_PER_WINDOW, PROVIDER_SPEND_MICROS_PER_WINDOW
    }
    public enum Intent { CUSTOMER_STAGING, CUSTOMER_PRODUCTION }
    public enum State { RESERVED, PROVISIONING, ACTIVE, OUTCOME_UNKNOWN, CLEANUP_FAILED, RELEASED }
    public enum Activity { CONFIGURATION_WRITE, ACCEPTED_SESSION, ACCEPTED_ACTION, HUMAN_WORK }
    public enum ExpiryState { CURRENT, NOTICE_PENDING, NOTICE_FAILED, NOTIFIED, CLAIMED, BLOCKED, HANDED_OFF, RELEASED }
    public enum NoticeChannel { CONSOLE, OWNER_EMAIL }

    public record Vector(Map<Dimension, Long> amounts) {
        public Vector { amounts = amounts == null ? null : Map.copyOf(amounts); }
    }
    /** Baseline includes existing non-ledger Platform/demo/build load; headroom protects overlap/recovery. */
    public record Budget(Vector measured, Vector baseline, Vector headroom, String measurementReference,
                         Instant measuredAt, Instant validUntil) { }
    public record Placement(String serverUuid, String destinationUuid, String ip) { }
    public record TargetBudget(String targetProfileId, Intent intent, Placement placement,
                               String profileFingerprint, Budget budget) { }
    public record LifecyclePolicy(long inactivitySeconds, long noticeLeadSeconds, long extensionSeconds,
                                  int maxExtensions, long maxLifetimeSeconds, long maxKeepSeconds,
                                  long drainSeconds, long recoverySeconds, long retrySeconds,
                                  int maxAttempts, long readbackMaxAgeSeconds, long budgetWindowSeconds,
                                  String policyReference, String noticeTransportReference) { }
    public record FleetPolicy(String id, Budget fleet, Map<String, TargetBudget> targets,
                              LifecyclePolicy lifecycle, String restoreEvidenceReference,
                              String privacyEvidenceReference, String supportReference) {
        public FleetPolicy { targets = targets == null ? null : Map.copyOf(targets); }
    }
    public record CustomerGrant(String customerId, Vector fleetLimit, Map<String, Vector> targetLimits,
                                Instant validUntil, String ownerContactEvidenceReference,
                                String payerReference, String keepStagingEntitlementReference) {
        public CustomerGrant { targetLimits = targetLimits == null ? null : Map.copyOf(targetLimits); }
    }
    /** Reviewed full topology envelope: runtime, workers, DB/volumes, indexes and R5 dependencies. */
    public record Manifest(String id, String deploymentId, String versionId, String versionConfigHash,
                           String targetProfileId, String bindingFingerprint, String topologyReference,
                           Vector demand, Set<String> prerequisiteReferences,
                           Map<Prerequisite, String> customerPrerequisiteReferences) {
        public Manifest {
            prerequisiteReferences = prerequisiteReferences == null ? null : Collections.unmodifiableSet(new TreeSet<>(prerequisiteReferences));
            customerPrerequisiteReferences = customerPrerequisiteReferences == null
                ? Map.of()
                : Map.copyOf(customerPrerequisiteReferences);
        }
        public Manifest(String id, String deploymentId, String versionId, String versionConfigHash,
                        String targetProfileId, String bindingFingerprint, String topologyReference,
                        Vector demand, Set<String> prerequisiteReferences) {
            this(id, deploymentId, versionId, versionConfigHash, targetProfileId, bindingFingerprint,
                topologyReference, demand, prerequisiteReferences, Map.of());
        }
    }
    /** Staff preflight input. Authority material is always derived from the immutable published version. */
    public record RuntimeAuthorityCandidateRequest(String deploymentId, String targetProfileId, String versionId) { }
    /** Safe nonsecret evidence that can be pinned by the reviewed admission manifest. */
    public record RuntimeAuthorityCandidateEvidence(String deploymentId, String targetProfileId, String versionId,
                                                    String snapshotId, String fingerprint, long generation,
                                                    List<String> allowedOrigins) {
        public RuntimeAuthorityCandidateEvidence {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }
    /**
     * Customer prerequisite scope is optional for staff-owned admission work, but is mandatory when the
     * reservation is later used as R6 customer prerequisite evidence. The full context is fingerprinted into
     * the durable reservation; no caller-supplied boolean can turn an ordinary reservation into customer proof.
     */
    public record Reserve(String operationKey, String manifestId,
                          com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext customerContext,
                          String allocationId) {
        public Reserve(String operationKey, String manifestId) { this(operationKey, manifestId, null, null); }
        public Reserve(String operationKey, String manifestId,
                       com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext customerContext) {
            this(operationKey, manifestId, customerContext, null);
        }
    }
    public record AuthorizationReceipt(String reservationId, String generation, String manifestFingerprint,
                                       String requestFingerprint, String contextFingerprint, Reserve command) { }
    public record ReleaseBinding(String releaseId, String reservationId, String generation,
                                 String authorizationKey, String versionId, String versionConfigHash,
                                 String customerId, String tenantId, String deploymentId,
                                 String targetProfileId, String state) { }
    public record ActivitySignal(Activity kind, long sequence, Instant acceptedAt) { }
    public record ActivityReadback(String schemaVersion, String deploymentId, String tenantId,
                                   String targetProfileId, String releaseId, Instant observedAt,
                                   java.util.List<ActivitySignal> signals) { }
    public record Fence(String reservationId, long revision, String generation) { }
    public record CancelUnusedReservation(
                                           @JsonProperty(value = "fence", required = true) @JsonSetter(nulls = Nulls.FAIL)
                                           @JsonDeserialize(using = CancelUnusedFenceDeserializer.class) Fence fence,
                                           String operationKey, String authorizationOperationKey,
                                           String authorizationRequestFingerprint, String manifestFingerprint,
                                           String versionId, String versionConfigHash, String bindingFingerprint,
                                           List<UnusedConsumerDetachment> consumerDetachments) {
        public CancelUnusedReservation {
            if (consumerDetachments != null && consumerDetachments.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("Consumer detachment expectations must not contain null entries.");
            consumerDetachments = consumerDetachments == null ? List.of() : consumerDetachments.stream()
                .sorted(java.util.Comparator.comparing(UnusedConsumerDetachment::consumerEntityId,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).toList();
        }
        public CancelUnusedReservation(Fence fence, String operationKey, String authorizationOperationKey,
                                       String authorizationRequestFingerprint, String manifestFingerprint,
                                       String versionId, String versionConfigHash, String bindingFingerprint) {
            this(fence, operationKey, authorizationOperationKey, authorizationRequestFingerprint, manifestFingerprint,
                versionId, versionConfigHash, bindingFingerprint, List.of());
        }
    }
    /** Cancellation requires explicit JSON evidence; other commands retain the shared Fence contract. */
    public static final class CancelUnusedFenceDeserializer extends JsonDeserializer<Fence> {
        @Override public Fence deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode value = parser.getCodec().readTree(parser);
            if (!value.isObject()) return context.reportInputMismatch(Fence.class, "Cancellation fence must be an object.");
            JsonNode reservationId = value.get("reservationId");
            JsonNode revision = value.get("revision");
            JsonNode generation = value.get("generation");
            if (reservationId == null || !reservationId.isTextual() || generation == null || !generation.isTextual()
                || revision == null || !revision.isIntegralNumber() || !revision.canConvertToLong())
                return context.reportInputMismatch(Fence.class,
                    "Cancellation fence requires textual reservationId and generation and an explicit integral long revision.");
            return new Fence(reservationId.textValue(), revision.longValue(), generation.textValue());
        }
    }
    public record DraftWorkspaceExpectation(String installationEntityId,
            @JsonProperty(value = "expectedRowVersion", required = true) @JsonSetter(nulls = Nulls.FAIL) long expectedRowVersion) { }
    public record UnusedConsumerDetachment(String consumerEntityId,
            @JsonProperty(value = "expectedBindingRevision", required = true) @JsonSetter(nulls = Nulls.FAIL) long expectedBindingRevision,
                                            List<DraftWorkspaceExpectation> draftWorkspaces) {
        public UnusedConsumerDetachment {
            if (draftWorkspaces != null && draftWorkspaces.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("Draft workspace expectations must not contain null entries.");
            if (draftWorkspaces != null) draftWorkspaces = draftWorkspaces.stream()
                .sorted(java.util.Comparator.comparing(DraftWorkspaceExpectation::installationEntityId,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).toList();
        }
    }
    public record UnusedConsumerDetachmentReceipt(String consumerEntityId, long fromBindingRevision,
                                                   long toBindingRevision, String historyId, String operationKey,
                                                   List<DraftWorkspaceExpectation> preservedDraftWorkspaces) {
        public UnusedConsumerDetachmentReceipt { preservedDraftWorkspaces = List.copyOf(preservedDraftWorkspaces); }
    }
    public enum BindingCancellationDisposition { PRESERVED_METADATA, RETAINED_UNUSED }
    public record RetainedBindingMetadata(String handleId, String resourceKind, String identityFingerprint,
                                           BindingCancellationDisposition disposition) { }
    public record UnusedAuthorityCandidate(String snapshotId, String fingerprint, long generation) { }
    public record UnusedTargetGate(String gateId, long epoch, long rowVersion) { }
    /** Local no-effect evidence only; neither external absence nor provider health is asserted. */
    public record UnusedReservationEvidence(List<RetainedBindingMetadata> retainedBindings,
                                             List<UnusedAuthorityCandidate> authorityCandidates, UnusedTargetGate targetGate) {
        public UnusedReservationEvidence(List<RetainedBindingMetadata> retainedBindings, List<UnusedAuthorityCandidate> authorityCandidates) {
            this(retainedBindings, authorityCandidates, null);
        }
        public UnusedReservationEvidence {
            retainedBindings = List.copyOf(retainedBindings);
            authorityCandidates = List.copyOf(authorityCandidates);
        }
    }
    public enum CancellationOutcome { CANCELLED_UNUSED_NO_EFFECT }
    public record UnusedReservationCancellationReceipt(String id, String operationKey, String commandFingerprint,
                                                        String authorizationOperationKey, String versionId,
                                                        String versionConfigHash, CancellationOutcome outcome,
                                                        boolean reclamationPerformed, String actorId,
                                                        Instant cancelledAt, UnusedReservationEvidence evidence,
                                                        List<UnusedConsumerDetachmentReceipt> consumerDetachments,
                                                        Reservation reservation) {
        public UnusedReservationCancellationReceipt { consumerDetachments = List.copyOf(consumerDetachments); }
    }
    public record Reservation(String id, String customerId, String tenantId, String deploymentId,
                              String targetProfileId, Intent intent, String operationKey, String requestFingerprint,
                              String generation, long revision, State state, Vector demand, String policyId,
                              String manifestId, String manifestFingerprint, String bindingFingerprint,
                              Instant createdAt, Instant updatedAt, Instant lastActivityAt, Instant expiresAt,
                              int extensions, Instant keepUntil, String keepEntitlementReference,
                              ExpiryState expiryState, String noticeCycle, Instant nextRetryAt, int attempts,
                              String workerToken, Instant drainDeadline, Instant recoveryUntil,
                              String retirementOperationId, String evidenceReference,
                              String failureCode, String customerContextFingerprint,
                              Operation customerOperation, PrerequisiteReadback prerequisiteReadback) {
        public Fence fence() { return new Fence(id, revision, generation); }
        public boolean consumesCapacity() { return state != State.RELEASED; }
    }
    public record AdmissionContext(String customerId, String tenantId, String deploymentId,
                                   FleetPolicy policy, CustomerGrant grant, Manifest manifest,
                                   String manifestFingerprint, TargetBudget target) { }
    public record PrerequisiteReadback(Instant observedAt, Instant validUntil, String evidenceReference,
                                       Placement placement, String profileFingerprint,
                                       String manifestFingerprint, String bindingFingerprint,
                                       Set<String> verifiedPrerequisiteReferences,
                                       String restoreEvidenceReference, String privacyEvidenceReference,
                                       String ownerContactEvidenceReference, String noticeTransportReference) { }
    public record NoticeWork(Fence fence, String noticeCycle, NoticeChannel channel,
                             String deliveryKey, Instant expiresAt, String transportReference) { }
    public record NoticeDelivery(String deliveryKey, String evidenceReference, Instant deliveredAt) { }
    /** A claim authorizes planning/draining only, not deletion. Main/R3 must supply real drain/inventory. */
    public record ExpiryWork(Fence fence, String workerToken, String noticeCycle, Instant expiresAt,
                             Instant drainDeadline, Instant recoveryUntil, String reason) { }
    public record ReadbackContext(Reservation reservation, FleetPolicy policy, String workerToken) { }
    public record ResourceReadback(String reservationId, String generation, long expectedRevision,
                                   String workerToken, String manifestFingerprint, String bindingFingerprint,
                                   Placement placement, Instant observedAt, String evidenceReference,
                                   boolean inventoryComplete, boolean outcomeSettled, boolean resourcesAbsent,
                                   boolean ingressBlocked, boolean retentionVerified,
                                   boolean assignmentsVerified, long assignmentCount, Long sessions,
                                   Long confirmations, Long humanWork, Long backgroundWork) { }
}
