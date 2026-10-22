package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ProviderVolumeGeneration;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ResourceGeneration;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Snapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/**
 * Pure, staff-attended eligibility review for a used, failed, never-activated staging pilot.
 * All inputs are server-owned captures, including expectations, absence confirmations and the
 * authenticated staff acknowledgement. Never deserialize browser-supplied snapshots or trust
 * browser absence booleans. Capture references must identify immutable, complete scoped evidence.
 *
 * This permits reviewing physical termination with loss, not drainComplete=true or zero work.
 * It performs no effects, supersedes no repair, and grants no final cleanup authority or hosted
 * readiness. Main must bind/revalidate native and dependency/Inbox evidence, establish a newer
 * lifecycle fence and obtain final authority before any later execution. Historical repair rows
 * remain unchanged; NO_ACTIVE_APPLY_OR_REPAIR means no currently authorized writer, not no history.
 * Neither expiry nor a held worker token proves an inactive process or a settled remote outcome.
 */
public final class AdmissionColdTerminationReview {
    private static final Duration MAX_REVIEW_WINDOW = Duration.ofMinutes(30);
    public enum Confirmation {
        NEVER_ACTIVATED, NO_ACTIVE_WORKSPACE, NO_ACTIVE_APPLY_OR_REPAIR, EXPIRY_WORKER_HELD,
        NO_RETIREMENT_OPERATION, COMPLETE_SCOPED_INVENTORY, EXCLUSIVE_NATIVE_OWNERSHIP
    }
    public enum LossAcknowledgement { DOWNTIME, AI_STATE_LOSS, WAIVE_RECOVERY_WINDOW }
    public enum Semantics { PHYSICAL_TERMINATION_WITH_LOSS_NOT_DRAIN }
    public enum AccountingDisposition { PRESERVE_HISTORY_AND_ACTUAL_USAGE_NO_REFUND_OR_BUDGET_RESET }

    public record Expectation(Fence fence, String customerId, String tenantId, String deploymentId,
                              String targetProfileId, String expiryWorkerToken) { }

    /** Exclusive ownership includes native volumes and absence of foreign/shared business references. */
    public record ServerCapture(String reference, Instant capturedAt, Instant validUntil,
                                Reservation reservation, Snapshot snapshot, List<ReleaseBinding> releaseBindings,
                                Placement nativePlacement, String projectUuid, String environmentUuid,
                                Set<Confirmation> confirmations) {
        public ServerCapture {
            reservation = immutableReservation(reservation);
            snapshot = immutableSnapshot(snapshot);
            releaseBindings = immutableList(releaseBindings);
            confirmations = immutableEnums(Confirmation.class, confirmations);
        }
    }

    /** The server records authenticated staff acceptance against the exact immutable capture reference. */
    public record Acknowledgement(String reviewReference, String captureReference, String staffActorId,
                                  String reason, Instant acknowledgedAt, Instant validUntil,
                                  Set<LossAcknowledgement> acceptedLosses) {
        public Acknowledgement {
            acceptedLosses = immutableEnums(LossAcknowledgement.class, acceptedLosses);
        }
    }

    /** Journal data only. Deserializing this record never creates a validated review or cleanup authority. */
    public record Payload(Expectation expectation, ServerCapture capture, Acknowledgement acknowledgement,
                          Instant frozenAt, Semantics semantics, AccountingDisposition accountingDisposition) { }

    private static final Set<String> NATIVE_KINDS = Set.of("APPLICATION", "CONNECTOR_APPLICATION",
        "VECTORIZATION_RUNNER_APPLICATION", "RUNTIME_POSTGRES_DATABASE");
    private final Expectation expectation;
    private final ServerCapture capture;
    private final Acknowledgement acknowledgement;
    private final Instant frozenAt;

    private AdmissionColdTerminationReview(Expectation expectation, ServerCapture capture,
                                           Acknowledgement acknowledgement, Instant frozenAt) {
        this.expectation = expectation;
        this.capture = capture;
        this.acknowledgement = acknowledgement;
        this.frozenAt = frozenAt;
    }

    public static AdmissionColdTerminationReview freeze(Expectation expected, ServerCapture capture,
                                                         Acknowledgement acknowledgement, Instant now) {
        require(expected != null && expected.fence() != null && capture != null
            && capture.reservation() != null && capture.snapshot() != null && acknowledgement != null,
            "COLD_REVIEW_INPUT_REQUIRED");
        text(expected.fence().reservationId(), expected.fence().generation(), expected.customerId(),
            expected.tenantId(), expected.deploymentId(), expected.targetProfileId(), expected.expiryWorkerToken());
        Reservation r = capture.reservation();
        Snapshot s = capture.snapshot();
        require(expected.fence().revision() >= 0 && expected.fence().equals(r.fence())
            && expected.customerId().equals(r.customerId()) && expected.customerId().equals(s.customerId())
            && expected.tenantId().equals(r.tenantId()) && expected.tenantId().equals(s.tenantId())
            && expected.deploymentId().equals(r.deploymentId()) && expected.deploymentId().equals(s.deploymentId())
            && expected.targetProfileId().equals(r.targetProfileId())
            && expected.targetProfileId().equals(s.targetProfileId()), "COLD_REVIEW_SCOPE_CHANGED");
        require(r.intent() == Intent.CUSTOMER_STAGING
            && !"dtp-coolify-prod-staging".equals(r.targetProfileId())
            && (r.state() == State.OUTCOME_UNKNOWN || r.state() == State.CLEANUP_FAILED)
            && r.retentionSettlement() == null, "COLD_REVIEW_FAILED_USED_STAGING_REQUIRED");
        require(r.expiryState() == ExpiryState.CLAIMED && expected.expiryWorkerToken().equals(r.workerToken())
            && r.retirementOperationId() == null, "COLD_REVIEW_EXACT_EXPIRY_CLAIM_REQUIRED");
        require(capture.confirmations() != null
            && capture.confirmations().containsAll(Set.of(Confirmation.values())),
            "COLD_REVIEW_SERVER_CONFIRMATIONS_REQUIRED");
        validateTimes(capture, acknowledgement, now);
        text(r.manifestFingerprint(), r.bindingFingerprint(), r.noticeCycle(), s.targetGeneration(),
            capture.reference(), capture.projectUuid(), capture.environmentUuid());
        require(s.targetEpoch() >= 0, "COLD_REVIEW_TARGET_GENERATION_REQUIRED");
        AdmissionRules.placement(Intent.CUSTOMER_STAGING, capture.nativePlacement());
        validateInventory(capture);
        text(acknowledgement.reviewReference(), acknowledgement.captureReference(),
            acknowledgement.staffActorId(), acknowledgement.reason());
        require(capture.reference().equals(acknowledgement.captureReference())
            && acknowledgement.acceptedLosses() != null
            && acknowledgement.acceptedLosses().containsAll(Set.of(LossAcknowledgement.values())),
            "COLD_REVIEW_EXPLICIT_LOSS_ACKNOWLEDGEMENT_REQUIRED");
        return new AdmissionColdTerminationReview(expected, capture, acknowledgement, now);
    }

    public Expectation expectation() { return expectation; }
    public ServerCapture capture() { return capture; }
    public Acknowledgement acknowledgement() { return acknowledgement; }
    public Instant frozenAt() { return frozenAt; }
    public Payload payload() {
        return new Payload(expectation, capture, acknowledgement, frozenAt, semantics(), accountingDisposition());
    }
    public Semantics semantics() { return Semantics.PHYSICAL_TERMINATION_WITH_LOSS_NOT_DRAIN; }
    public AccountingDisposition accountingDisposition() {
        return AccountingDisposition.PRESERVE_HISTORY_AND_ACTUAL_USAGE_NO_REFUND_OR_BUDGET_RESET;
    }

    /** Review candidates only, never a deletion command, effect receipt or capacity release. */
    public List<ResourceGeneration> nativeTerminationCandidates() {
        return capture.snapshot().resources().stream().filter(resource -> !resource.preserved()).toList();
    }

    /** These handles and their external data are never eligible for deletion under this review. */
    public List<ResourceGeneration> preservedExternalResources() {
        return capture.snapshot().resources().stream().filter(ResourceGeneration::preserved).toList();
    }

    private static void validateTimes(ServerCapture capture, Acknowledgement ack, Instant now) {
        Reservation r = capture.reservation();
        require(finite(now) && finite(capture.capturedAt()) && finite(capture.validUntil())
            && finite(ack.acknowledgedAt()) && finite(ack.validUntil())
            && !capture.capturedAt().isAfter(ack.acknowledgedAt()) && !ack.acknowledgedAt().isAfter(now)
            && ack.validUntil().isAfter(now) && !ack.validUntil().isAfter(capture.validUntil()),
            "COLD_REVIEW_FINITE_CURRENT_ACKNOWLEDGEMENT_REQUIRED");
        require(Duration.between(capture.capturedAt(), capture.validUntil()).compareTo(MAX_REVIEW_WINDOW) <= 0,
            "COLD_REVIEW_WINDOW_TOO_LONG");
        require(finite(r.createdAt()) && finite(r.updatedAt()) && finite(r.expiresAt())
            && r.expiresAt().isAfter(r.createdAt()) && !r.expiresAt().isAfter(capture.capturedAt())
            && !r.updatedAt().isBefore(r.createdAt()) && !r.updatedAt().isAfter(capture.capturedAt())
            && (r.keepUntil() == null || finite(r.keepUntil()) && !r.keepUntil().isAfter(capture.capturedAt()))
            && finite(r.drainDeadline()) && finite(r.recoveryUntil())
            && r.drainDeadline().isAfter(r.expiresAt()) && r.recoveryUntil().isAfter(r.drainDeadline()),
            "COLD_REVIEW_BOUNDED_EXPIRED_RESERVATION_REQUIRED");
        // Recovery need not have elapsed: its explicit waiver is mandatory even after that deadline.
    }

    private static void validateInventory(ServerCapture capture) {
        Reservation r = capture.reservation();
        Snapshot s = capture.snapshot();
        require(s.releases() != null && !s.releases().isEmpty() && s.resources() != null
            && !s.resources().isEmpty() && s.consumers() != null && capture.releaseBindings() != null,
            "COLD_REVIEW_COMPLETE_INVENTORY_REQUIRED");
        var releases = new HashMap<String, String>();
        for (var release : s.releases()) {
            text(release.releaseId(), release.versionId(), release.generation());
            require(releases.putIfAbsent(release.releaseId(), release.versionId()) == null,
                "COLD_REVIEW_DUPLICATE_RELEASE");
        }
        var bindingStates = new HashMap<String, String>();
        for (var binding : capture.releaseBindings()) {
            text(binding.authorizationKey(), binding.versionConfigHash());
            require(r.id().equals(binding.reservationId()) && r.generation().equals(binding.generation())
                && r.customerId().equals(binding.customerId()) && r.tenantId().equals(binding.tenantId())
                && r.deploymentId().equals(binding.deploymentId()) && r.targetProfileId().equals(binding.targetProfileId())
                && binding.versionId() != null && binding.versionId().equals(releases.get(binding.releaseId()))
                && ("OUTCOME_UNKNOWN".equals(binding.state()) || "NO_EFFECT".equals(binding.state())),
                "COLD_REVIEW_RELEASE_SCOPE_CHANGED");
            require(bindingStates.putIfAbsent(binding.releaseId(), binding.state()) == null,
                "COLD_REVIEW_DUPLICATE_RELEASE_BINDING");
        }
        require(bindingStates.keySet().equals(releases.keySet()), "COLD_REVIEW_RELEASE_SET_CHANGED");
        var consumers = new HashSet<String>();
        for (var consumer : s.consumers()) {
            text(consumer.consumerEntityId());
            require(consumer.expectedBindingRevision() >= 0 && consumers.add(consumer.consumerEntityId()),
                "COLD_REVIEW_CONSUMER_EXPECTATION_INVALID");
        }
        var handles = new HashSet<String>();
        var nativeIds = new HashSet<String>();
        var volumeIds = new HashSet<String>();
        var volumeNames = new HashSet<String>();
        int nativeCount = 0;
        for (var resource : s.resources()) {
            text(resource.handleId(), resource.resourceKind(), resource.providerResourceUuid(), resource.generation());
            require(handles.add(resource.handleId()) && resource.providerType() != null,
                "COLD_REVIEW_RESOURCE_IDENTITY_INVALID");
            require(resource.releaseId() == null || releases.containsKey(resource.releaseId()),
                "COLD_REVIEW_RESOURCE_RELEASE_CHANGED");
            boolean external = PreservedExternalBindingKinds.isPreserved(resource.resourceKind());
            require(resource.preserved() == external, "COLD_REVIEW_EXTERNAL_PRESERVATION_REQUIRED");
            if (external) {
                require(resource.volumes().isEmpty(), "COLD_REVIEW_EXTERNAL_VOLUME_INVENTORY_UNSUPPORTED");
                continue;
            }
            require(NATIVE_KINDS.contains(resource.resourceKind())
                && resource.providerType() == DeploymentProviderType.COOLIFY
                && "OUTCOME_UNKNOWN".equals(bindingStates.get(resource.releaseId())),
                "COLD_REVIEW_NATIVE_FAILED_RESOURCE_REQUIRED");
            text(resource.serverUuid(), resource.projectUuid(), resource.environmentUuid(), resource.destinationUuid(),
                resource.providerName(), resource.providerIdentityFingerprint());
            boolean database = "RUNTIME_POSTGRES_DATABASE".equals(resource.resourceKind());
            String marker = (database ? "Durable runtime chat database for AI Fabric deployment "
                : "Managed by AI Fabric deployment ") + r.deploymentId();
            require(marker.equals(resource.deploymentMarker())
                && capture.nativePlacement().serverUuid().equals(resource.serverUuid())
                && capture.nativePlacement().destinationUuid().equals(resource.destinationUuid())
                && capture.projectUuid().equals(resource.projectUuid())
                && capture.environmentUuid().equals(resource.environmentUuid())
                && nativeIds.add(resource.providerResourceUuid())
                && s.resources().stream().noneMatch(other -> other.preserved()
                    && other.providerType() == resource.providerType()
                    && resource.providerResourceUuid().equals(other.providerResourceUuid())),
                "COLD_REVIEW_NATIVE_IDENTITY_CHANGED");
            require(!database || !resource.volumes().isEmpty(), "COLD_REVIEW_DATABASE_VOLUMES_REQUIRED");
            for (var volume : resource.volumes()) {
                text(volume.storageUuid(), volume.name(), volume.mountPath(), volume.generation());
                require("persistent".equals(volume.type()) && volume.mountPath().startsWith("/")
                    && volumeIds.add(volume.storageUuid()) && volumeNames.add(volume.name())
                    && (!database || !volume.preserved())
                    && (volume.preserved() ? volume.hostPath() != null && volume.hostPath().startsWith("/")
                        : volume.hostPath() == null || volume.hostPath().isBlank()),
                    "COLD_REVIEW_VOLUME_IDENTITY_OR_OWNERSHIP_INVALID");
            }
            nativeCount++;
        }
        require(nativeCount > 0, "COLD_REVIEW_PROVISIONED_USED_RESOURCE_REQUIRED");
    }

    private static Snapshot immutableSnapshot(Snapshot s) {
        return s == null ? null : new Snapshot(s.customerId(), s.tenantId(), s.deploymentId(), s.targetProfileId(),
            s.targetGeneration(), s.targetEpoch(), immutableList(s.releases()), immutableList(s.resources()),
            immutableList(s.consumers()));
    }

    private static Reservation immutableReservation(Reservation r) {
        if (r == null || r.prerequisiteReadback() == null) return r;
        var p = r.prerequisiteReadback();
        var frozen = new PrerequisiteReadback(p.observedAt(), p.validUntil(), p.evidenceReference(), p.placement(),
            p.profileFingerprint(), p.manifestFingerprint(), p.bindingFingerprint(),
            p.verifiedPrerequisiteReferences() == null ? null
                : Collections.unmodifiableSet(new TreeSet<>(p.verifiedPrerequisiteReferences())),
            p.restoreEvidenceReference(), p.privacyEvidenceReference(), p.ownerContactEvidenceReference(), p.noticeTransportReference());
        return new Reservation(r.id(), r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.intent(),
            r.operationKey(), r.requestFingerprint(), r.generation(), r.revision(), r.state(), r.demand(), r.policyId(),
            r.manifestId(), r.manifestFingerprint(), r.bindingFingerprint(), r.createdAt(), r.updatedAt(), r.lastActivityAt(),
            r.expiresAt(), r.extensions(), r.keepUntil(), r.keepEntitlementReference(), r.expiryState(), r.noticeCycle(),
            r.nextRetryAt(), r.attempts(), r.workerToken(), r.drainDeadline(), r.recoveryUntil(), r.retirementOperationId(),
            r.evidenceReference(), r.failureCode(), r.customerContextFingerprint(), r.customerOperation(), frozen,
            r.retentionEnvelope(), r.retentionSettlement());
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null) return null;
        require(values.stream().noneMatch(java.util.Objects::isNull), "COLD_REVIEW_NULL_INVENTORY_ENTRY");
        return List.copyOf(values);
    }

    private static <E extends Enum<E>> Set<E> immutableEnums(Class<E> type, Set<E> values) {
        if (values == null) return null;
        var copy = EnumSet.noneOf(type);
        copy.addAll(values);
        return Collections.unmodifiableSet(copy);
    }

    private static boolean finite(Instant value) {
        return value != null && !Instant.MIN.equals(value) && !Instant.MAX.equals(value);
    }

    private static void text(String... values) {
        for (String value : values) AdmissionRules.required(value, 255);
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
