package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.PrerequisiteEvidence;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedLimits;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerPrerequisiteEvidenceProvider;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/**
 * R6/R7 bridge backed only by durable local records. It deliberately has no provider client and can run while
 * the customer command owns entitlement/deployment locks. Recurring source probes are stored separately
 * from the immutable admission readback and must finish before the source command is authorized.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class DeploymentAdmissionCustomerPrerequisiteEvidenceProvider implements CustomerPrerequisiteEvidenceProvider {
    private final AdmissionStore store;
    private final AdmissionJson json;
    private final ObjectMapper mapper;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final CustomerTargetPlacementService placement;
    private final CustomerSourcePrerequisiteEvidenceStore sourceEvidence;
    private final DeploymentTargetContextService targetContexts;
    private final Clock clock;

    @Autowired
    public DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(AdmissionStore store, AdmissionJson json,
        ObjectMapper mapper, DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, CustomerTargetPlacementService placement,
        CustomerSourcePrerequisiteEvidenceStore sourceEvidence, DeploymentTargetContextService targetContexts) {
        this(store, json, mapper, deployments, versions, targets, placement, sourceEvidence, targetContexts, Clock.systemUTC());
    }

    DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(AdmissionStore store, AdmissionJson json,
        ObjectMapper mapper, DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, CustomerTargetPlacementService placement,
        CustomerSourcePrerequisiteEvidenceStore sourceEvidence, DeploymentTargetContextService targetContexts, Clock clock) {
        this.store = store;
        this.json = json;
        this.mapper = mapper;
        this.deployments = deployments;
        this.versions = versions;
        this.targets = targets;
        this.placement = placement;
        this.sourceEvidence = sourceEvidence;
        this.targetContexts = targetContexts;
        this.clock = clock;
    }

    @Override
    public PrerequisiteEvidence resolve(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        requireRequest(requested);
        if (isSourceOperation(requested.operation())) return resolveSource(requested);
        var matches = store.customerPrerequisiteReservations(requested);
        if (matches.size() != 1) throw unavailable("CUSTOMER_CAPACITY_RESERVATION_AMBIGUOUS_OR_MISSING");
        Reservation reservation = matches.getFirst();
        Instant now = clock.instant();
        if ((reservation.state() != State.RESERVED && reservation.state() != State.PROVISIONING && reservation.state() != State.ACTIVE)
            || !reservation.consumesCapacity() || reservation.expiresAt() == null || !reservation.expiresAt().isAfter(now)
            || reservation.expiryState() == ExpiryState.CLAIMED || reservation.expiryState() == ExpiryState.BLOCKED
            || reservation.expiryState() == ExpiryState.HANDED_OFF || reservation.expiryState() == ExpiryState.RELEASED) {
            throw unavailable("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        }
        store.requireCustomerAuthorization(reservation, requested);

        AdmissionContext current = store.context(requested.customerId(), requested.tenantId(), requested.deploymentId(),
            reservation.manifestId());
        validateCurrentReservation(requested, reservation, current, now, true);
        validateDeploymentAndVersion(requested, current);
        validateTarget(current.target());
        CustomerPlacementEvidenceEntity placementEvidence = placement.requireEvidenceById(
            targets.findById(requested.targetProfileId()).orElseThrow(() -> unavailable("CUSTOMER_TARGET_PROFILE_MISSING")),
            requested.intent(), requested.placementEvidenceId());
        validatePlacementEvidence(requested, current.target(), placementEvidence, now);
        validateReviewedLimits(requested.limits(), current);

        PrerequisiteReadback readback = reservation.prerequisiteReadback();
        if (!current.manifest().prerequisiteReferences().equals(readback.verifiedPrerequisiteReferences())) {
            throw unavailable("CUSTOMER_PREREQUISITE_EVIDENCE_UNKNOWN");
        }
        Instant expiresAt = minimum(reservation.expiresAt(), readback.validUntil(), current.grant().validUntil(),
            current.policy().fleet().validUntil(), current.target().budget().validUntil(), placementEvidence.getExpiresAt());
        if (!expiresAt.isAfter(now)) throw unavailable("CUSTOMER_PREREQUISITE_EVIDENCE_EXPIRED");
        EnumSet<Prerequisite> passed = EnumSet.of(Prerequisite.CAPACITY);
        current.manifest().customerPrerequisiteReferences().forEach((prerequisite, reference) -> {
            if (readback.verifiedPrerequisiteReferences().contains(reference)) passed.add(prerequisite);
        });
        String evidenceReference = "admission:" + reservation.id() + ":" + reservation.generation()
            + ":" + reservation.revision();
        return new PrerequisiteEvidence(requested, passed, readback.observedAt(),
            expiresAt, evidenceReference, reservation.id());
    }

    private PrerequisiteEvidence resolveSource(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        SourceSnapshot snapshot = sourceSnapshot(requested);
        Instant now = clock.instant();
        var evidence = sourceEvidence.latest(snapshot.scope())
            .orElseThrow(() -> unavailable("SOURCE_PREREQUISITE_REFRESH_REQUIRED"));
        var profile = snapshot.runtime().target();
        var requestedPlacement = placement.requireEvidenceById(profile, requested.intent(), requested.placementEvidenceId());
        validatePlacementEvidence(requested, snapshot.target(), requestedPlacement, now);
        var collectedPlacement = placement.requireEvidenceById(profile, requested.intent(), evidence.placementEvidenceId());
        if (!snapshot.scope().profileFingerprint().equals(collectedPlacement.getProfileFingerprint())
            || evidence.checkedAt() == null || evidence.checkedAt().isAfter(now)
            || evidence.checkedAt().isBefore(now.minusSeconds(snapshot.maxAgeSeconds()))
            || evidence.expiresAt() == null || !evidence.expiresAt().isAfter(now)
            || blank(evidence.connectorProofDigest()) || !evidence.passed().contains(Prerequisite.STORAGE)) {
            throw unavailable("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        }
        Instant expiry = minimum(snapshot.expiresAt(), evidence.expiresAt(), collectedPlacement.getExpiresAt(),
            requestedPlacement.getExpiresAt());
        if (!expiry.isAfter(now)) throw unavailable("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        return new PrerequisiteEvidence(requested, evidence.passed(), evidence.checkedAt(), expiry,
            "source-prerequisites:" + evidence.id(), snapshot.scope().reservationId());
    }

    /** Database-only snapshot used before/after the remote probe and by the owning source command. */
    public SourceSnapshot sourceSnapshot(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        if (requested == null || !isSourceOperation(requested.operation()) || blank(requested.releaseId())) {
            throw unavailable("CUSTOMER_PREREQUISITE_CONTEXT_INVALID");
        }
        var allocation = store.customerPrerequisiteReleaseAllocation(requested);
        validateReleaseBoundSourceScope(requested, allocation);
        Reservation reservation = allocation.reservation();
        Instant now = clock.instant();
        if ((reservation.state() != State.ACTIVE && reservation.state() != State.PROVISIONING
                && reservation.state() != State.OUTCOME_UNKNOWN) || !reservation.consumesCapacity()
            || reservation.expiresAt() == null || !reservation.expiresAt().isAfter(now)
            || reservation.expiryState() == ExpiryState.CLAIMED || reservation.expiryState() == ExpiryState.BLOCKED
            || reservation.expiryState() == ExpiryState.HANDED_OFF || reservation.expiryState() == ExpiryState.RELEASED) {
            throw unavailable("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        }
        AdmissionContext current = store.context(requested.customerId(), requested.tenantId(), requested.deploymentId(),
            reservation.manifestId());
        validateCurrentReservation(requested, reservation, current, now, false);
        validateReleaseVersion(allocation.release(), current);
        validateDeploymentAndVersion(requested, current);
        validateTarget(current.target());
        validateReviewedLimits(requested.limits(), current);
        var runtime = targetContexts.resolve(requested.deploymentId(), requested.targetProfileId(), requested.releaseId(), false);
        validateSourceReleaseLifecycle(allocation, runtime.release());
        if (!allocation.release().versionId().equals(runtime.version().getId())
            || !allocation.release().versionConfigHash().equals(runtime.version().getConfigHash())
            || blank(runtime.release().getDocumentBindingFingerprint())) throw unavailable("ACTIVE_RELEASE_VERSION_CHANGED");
        var binding = json.read(runtime.release().getDocumentBindingSnapshotJson(), com.fasterxml.jackson.databind.JsonNode.class);
        long bindingRevision = binding.path("safeMetadata").path("bindingRevision").asLong();
        if (bindingRevision <= 0 || !binding.path("secretRevisions").isObject() || binding.path("secretRevisions").isEmpty()
            || blank(binding.path("resourceHandleId").asText())) throw unavailable("SOURCE_STORAGE_BINDING_EVIDENCE_REQUIRED");
        var scope = new CustomerSourcePrerequisiteEvidence.Scope(requested.customerId(), requested.tenantId(),
            requested.deploymentId(), requested.targetProfileId(), requested.releaseId(), requested.intent(),
            requested.offerId(), requested.offerFingerprint(), requested.entitlementRevision(), reservation.id(),
            reservation.generation(), reservation.manifestFingerprint(), reservation.bindingFingerprint(),
            current.manifest().versionId(), current.manifest().versionConfigHash(), current.target().profileFingerprint(),
            runtime.release().getDocumentBindingFingerprint(), bindingRevision, json.fingerprint(binding.path("secretRevisions")),
            json.fingerprint(Map.of("runtimeBaseUrl", runtime.runtimeBaseUrl(),
                "resourceHandleId", runtime.release().getProviderResourceHandleId(),
                "runtimeAuthority", Objects.toString(runtime.release().getRuntimeAuthorityFingerprint(), ""))));
        EnumSet<Prerequisite> passed = EnumSet.of(Prerequisite.STORAGE);
        if (current.manifest().customerPrerequisiteReferences().containsKey(Prerequisite.IDENTITY)) passed.add(Prerequisite.IDENTITY);
        return new SourceSnapshot(scope, runtime, current.target(), binding.path("safeMetadata").path("connectorType").asText(),
            binding.path("resourceHandleId").asText(), passed,
            minimum(reservation.expiresAt(), current.grant().validUntil(), current.policy().fleet().validUntil(),
                current.target().budget().validUntil()), current.policy().lifecycle().readbackMaxAgeSeconds());
    }

    record SourceSnapshot(CustomerSourcePrerequisiteEvidence.Scope scope, DeploymentTargetContextService.Context runtime,
        TargetBudget target, String connectorType, String bindingRef, java.util.Set<Prerequisite> passed,
        Instant expiresAt, long maxAgeSeconds) { }

    private void validateReleaseBoundSourceScope(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested,
        AdmissionStore.ReleasePrerequisiteAllocation allocation) {
        var admitted = allocation.admittedContext();
        ReleaseBinding release = allocation.release();
        if (!java.util.Set.of("ACTIVE", "PROVISIONING", "OUTCOME_UNKNOWN").contains(release.state())
            || !requested.releaseId().equals(release.releaseId())
            || !requested.customerId().equals(release.customerId())
            || !requested.tenantId().equals(release.tenantId())
            || !requested.deploymentId().equals(release.deploymentId())
            || !requested.targetProfileId().equals(release.targetProfileId())
            || (admitted.operation() != com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY
                && admitted.operation() != com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.PROMOTION_APPLY)
            || !requested.customerId().equals(admitted.customerId())
            || !requested.tenantId().equals(admitted.tenantId())
            || !requested.deploymentId().equals(admitted.deploymentId())
            || !requested.targetProfileId().equals(admitted.targetProfileId())
            || requested.intent() != admitted.intent()
            || !requested.offerId().equals(admitted.offerId())
            || !requested.offerFingerprint().equals(admitted.offerFingerprint())
            || requested.entitlementRevision() != admitted.entitlementRevision()
            || !Objects.equals(requested.limits(), admitted.limits())) {
            throw unavailable("ACTIVE_RELEASE_PREREQUISITE_SCOPE_CHANGED");
        }
    }

    /** Source preparation is permitted before verification; this does not authorize consumer activation. */
    private void validateSourceReleaseLifecycle(AdmissionStore.ReleasePrerequisiteAllocation allocation,
        com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity runtime) {
        ReleaseBinding binding = allocation.release();
        Reservation reservation = allocation.reservation();
        boolean active = "ACTIVE".equals(binding.state()) && reservation.state() == State.ACTIVE;
        boolean preparing = "PROVISIONING".equals(binding.state()) && reservation.state() == State.PROVISIONING
            && java.util.Set.of("VERIFYING", "APPLIED_VERIFIED").contains(runtime.getStatus());
        boolean repairingVerification = "OUTCOME_UNKNOWN".equals(binding.state())
            && reservation.state() == State.OUTCOME_UNKNOWN && "APPLIED_VERIFICATION_FAILED".equals(runtime.getStatus());
        if (!binding.releaseId().equals(runtime.getId()) || !binding.deploymentId().equals(runtime.getDeploymentId())
            || !binding.targetProfileId().equals(runtime.getTargetProfileId())
            || !binding.versionId().equals(runtime.getDeploymentVersionId())
            || !"ACTIVE".equals(runtime.getProvisioningStatus())
            || !java.util.Set.of("VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED").contains(runtime.getStatus())
            || (!active && !preparing && !repairingVerification)) {
            throw unavailable("SOURCE_RELEASE_NOT_PROVISIONED_OR_ALLOCATION_UNSETTLED");
        }
    }

    private void validateReleaseVersion(ReleaseBinding release, AdmissionContext current) {
        if (!current.manifest().versionId().equals(release.versionId())
            || !current.manifest().versionConfigHash().equals(release.versionConfigHash())) {
            throw unavailable("ACTIVE_RELEASE_VERSION_CHANGED");
        }
    }

    private void validateCurrentReservation(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested,
        Reservation reservation, AdmissionContext current, Instant now, boolean requireFreshReadback) {
        policy(current.policy(), now);
        grant(current.grant(), now);
        manifest(current.manifest());
        if (requireFreshReadback) prerequisites(current, reservation.prerequisiteReadback(), now);
        if (!reservation.customerId().equals(current.customerId()) || !reservation.tenantId().equals(current.tenantId())
            || !reservation.deploymentId().equals(current.deploymentId())
            || !reservation.targetProfileId().equals(current.target().targetProfileId())
            || reservation.intent() != current.target().intent() || !reservation.policyId().equals(current.policy().id())
            || !reservation.manifestFingerprint().equals(current.manifestFingerprint())
            || !reservation.bindingFingerprint().equals(current.manifest().bindingFingerprint())
            || reservation.prerequisiteReadback() == null) {
            throw unavailable("CUSTOMER_ADMISSION_FINGERPRINT_CHANGED");
        }
        if (!requireFreshReadback) {
            PrerequisiteReadback original = reservation.prerequisiteReadback();
            if (!current.manifest().prerequisiteReferences().equals(original.verifiedPrerequisiteReferences())
                || !current.manifestFingerprint().equals(original.manifestFingerprint())
                || !current.manifest().bindingFingerprint().equals(original.bindingFingerprint())
                || !current.target().profileFingerprint().equals(original.profileFingerprint())
                || !current.target().placement().equals(original.placement())) {
                throw unavailable("CUSTOMER_PREREQUISITE_EVIDENCE_UNKNOWN");
            }
        }
    }

    private void validateDeploymentAndVersion(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested,
        AdmissionContext current) {
        DeploymentEntity deployment = deployments.findById(requested.deploymentId())
            .orElseThrow(() -> unavailable("CUSTOMER_DEPLOYMENT_MISSING"));
        if (!requested.customerId().equals(deployment.getCustomerId())
            || !requested.tenantId().equals(deployment.getTenantId()) || deployment.getArchivedAt() != null
            || "ARCHIVED".equalsIgnoreCase(deployment.getStatus()) || "DELETED".equalsIgnoreCase(deployment.getStatus())) {
            throw unavailable("CUSTOMER_DEPLOYMENT_SCOPE_CHANGED");
        }
        var version = versions.findById(current.manifest().versionId())
            .orElseThrow(() -> unavailable("CUSTOMER_REVIEWED_VERSION_MISSING"));
        if (!deployment.getId().equals(version.getDeploymentId())
            || !current.manifest().versionConfigHash().equals(version.getConfigHash())
            || !"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw unavailable("CUSTOMER_REVIEWED_VERSION_CHANGED");
        }
    }

    private void validateTarget(TargetBudget target) {
        var profile = targets.findById(target.targetProfileId())
            .orElseThrow(() -> unavailable("CUSTOMER_TARGET_PROFILE_MISSING"));
        if (!profile.isActive() || profile.getProviderType() != DeploymentProviderType.COOLIFY
            || "dtp-coolify-prod-staging".equals(profile.getId())) throw unavailable("CUSTOMER_TARGET_NOT_ADMISSIBLE");
        try {
            var config = mapper.readTree(profile.getProviderConfigJson());
            String fingerprint = json.fingerprint(Map.of("id", profile.getId(), "provider", profile.getProviderType(),
                "environment", profile.getEnvironmentName(), "sourceStrategy", profile.getSourceStrategy(),
                "credentialReference", profile.getCredentialRefId() == null ? "" : profile.getCredentialRefId(),
                "providerConfig", config, "networkPolicy", mapper.readTree(profile.getNetworkPolicyJson()),
                "resourceDefaults", mapper.readTree(profile.getResourceDefaultsJson())));
            if (!target.placement().serverUuid().equals(config.path("serverUuid").asText())
                || !target.placement().destinationUuid().equals(config.path("destinationUuid").asText())
                || !fingerprint.equals(target.profileFingerprint())) throw unavailable("CUSTOMER_TARGET_PROFILE_CHANGED");
            placement(target.intent(), target.placement());
        } catch (AdmissionFailure ex) { throw ex; }
        catch (Exception ex) { throw unavailable("CUSTOMER_TARGET_CONFIGURATION_INVALID"); }
    }

    private void validatePlacementEvidence(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested,
        TargetBudget target, CustomerPlacementEvidenceEntity evidence, Instant now) {
        if (evidence == null || !requested.placementEvidenceId().equals(evidence.getId())
            || !requested.targetProfileId().equals(evidence.getTargetProfileId())
            || requested.intent() != evidence.getIntent()
            || !target.profileFingerprint().equals(evidence.getProfileFingerprint())
            || !target.placement().serverUuid().equals(evidence.getServerUuid())
            || !target.placement().ip().equals(evidence.getServerAddress())
            || !target.placement().destinationUuid().equals(evidence.getDestinationUuid())
            || evidence.getCheckedAt() == null || evidence.getCheckedAt().isAfter(now)
            || evidence.getExpiresAt() == null || !evidence.getExpiresAt().isAfter(now)) {
            throw unavailable("CUSTOMER_PLACEMENT_EVIDENCE_MISMATCH");
        }
    }

    private void validateReviewedLimits(ReviewedLimits limits, AdmissionContext current) {
        if (limits == null || !limits.measuredPolicyRef().equals(current.policy().fleet().measurementReference()))
            throw unavailable("CUSTOMER_REVIEWED_LIMITS_CHANGED");
        Vector demand = current.manifest().demand();
        requireAtMost(demand, Dimension.REPLICAS, limits.replicas());
        requireAtMost(demand, Dimension.CPU_MILLICORES, limits.cpuMillis());
        requireAtMost(demand, Dimension.MEMORY_BYTES, mib(limits.memoryMiB()));
        requireAtMost(demand, Dimension.DISK_BYTES, mib(limits.diskMiB()));
        requireAtMost(demand, Dimension.DATABASES, limits.databases());
        requireAtMost(demand, Dimension.INDEXES, limits.indexes());
        requireAtMost(demand, Dimension.DEPENDENCIES, limits.dependencies());
        requireAtMost(demand, Dimension.CONCURRENT_APPLIES, limits.concurrentApplies());
    }

    private void requireAtMost(Vector demand, Dimension dimension, long reviewedLimit) {
        Long actual = demand == null || demand.amounts() == null ? null : demand.amounts().get(dimension);
        if (actual == null || actual < 0 || actual > reviewedLimit) throw unavailable("CUSTOMER_REVIEWED_LIMITS_CHANGED");
    }

    private long mib(long value) {
        try { return Math.multiplyExact(value, 1024L * 1024L); }
        catch (ArithmeticException ex) { throw unavailable("CUSTOMER_REVIEWED_LIMITS_CHANGED"); }
    }

    private void requireRequest(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        if (requested == null || blank(requested.customerId()) || blank(requested.tenantId())
            || blank(requested.deploymentId()) || blank(requested.targetProfileId()) || requested.intent() == null
            || blank(requested.offerId()) || blank(requested.offerFingerprint()) || requested.entitlementRevision() <= 0
            || blank(requested.placementEvidenceId()) || requested.operation() == null
            || (!isReadOnlySourceOperation(requested.operation()) && blank(requested.operationKey()))
            || (isSourceOperation(requested.operation()) && blank(requested.releaseId()))
            || requested.limits() == null) throw unavailable("CUSTOMER_PREREQUISITE_CONTEXT_INVALID");
    }

    private boolean isSourceOperation(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation operation) {
        return switch (operation) {
            case SOURCE_DISCOVER, SOURCE_PREVIEW, SOURCE_INDEX, SOURCE_REFRESH, SOURCE_REMOVE -> true;
            default -> false;
        };
    }

    private boolean isReadOnlySourceOperation(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation operation) {
        return operation == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_DISCOVER
            || operation == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_PREVIEW;
    }

    private Instant minimum(Instant first, Instant... rest) {
        if (first == null) throw unavailable("CUSTOMER_PREREQUISITE_EVIDENCE_UNKNOWN");
        Instant result = first;
        for (Instant candidate : rest) {
            if (candidate == null) throw unavailable("CUSTOMER_PREREQUISITE_EVIDENCE_UNKNOWN");
            if (candidate.isBefore(result)) result = candidate;
        }
        return result;
    }

    private AdmissionFailure unavailable(String code) { return new AdmissionFailure(PRECONDITION_FAILED, code); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
