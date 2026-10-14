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
 * the customer command owns entitlement/deployment locks. Remote prerequisite collection must finish before
 * the admission reservation is created.
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
    private final Clock clock;

    @Autowired
    public DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(AdmissionStore store, AdmissionJson json,
        ObjectMapper mapper, DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, CustomerTargetPlacementService placement) {
        this(store, json, mapper, deployments, versions, targets, placement, Clock.systemUTC());
    }

    DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(AdmissionStore store, AdmissionJson json,
        ObjectMapper mapper, DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, CustomerTargetPlacementService placement, Clock clock) {
        this.store = store;
        this.json = json;
        this.mapper = mapper;
        this.deployments = deployments;
        this.versions = versions;
        this.targets = targets;
        this.placement = placement;
        this.clock = clock;
    }

    @Override
    public PrerequisiteEvidence resolve(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        requireRequest(requested);
        var matches = store.customerPrerequisiteReservations(requested);
        if (matches.size() != 1) throw unavailable("CUSTOMER_CAPACITY_RESERVATION_AMBIGUOUS_OR_MISSING");

        Reservation reservation = matches.getFirst();
        Instant now = clock.instant();
        if ((reservation.state() != State.RESERVED && reservation.state() != State.PROVISIONING)
            || !reservation.consumesCapacity() || reservation.expiresAt() == null || !reservation.expiresAt().isAfter(now)
            || reservation.expiryState() == ExpiryState.CLAIMED || reservation.expiryState() == ExpiryState.BLOCKED
            || reservation.expiryState() == ExpiryState.HANDED_OFF || reservation.expiryState() == ExpiryState.RELEASED
            || reservation.customerOperation() != requested.operation()
            || !json.fingerprint(requested).equals(reservation.customerContextFingerprint())) {
            throw unavailable("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        }

        AdmissionContext current = store.context(requested.customerId(), requested.tenantId(), requested.deploymentId(),
            reservation.manifestId());
        validateCurrentReservation(requested, reservation, current, now);
        validateDeploymentAndVersion(requested, current);
        validateTarget(current.target());
        CustomerPlacementEvidenceEntity placementEvidence = placement.requireEvidenceById(
            targets.findById(requested.targetProfileId()).orElseThrow(() -> unavailable("CUSTOMER_TARGET_PROFILE_MISSING")),
            requested.intent(), requested.placementEvidenceId());
        if (placementEvidence == null) throw unavailable("CUSTOMER_PLACEMENT_EVIDENCE_UNKNOWN");
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

    private void validateCurrentReservation(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested,
        Reservation reservation, AdmissionContext current, Instant now) {
        policy(current.policy(), now);
        grant(current.grant(), now);
        manifest(current.manifest());
        prerequisites(current, reservation.prerequisiteReadback(), now);
        Reserve exactCommand = new Reserve(requested.operationKey(), current.manifest().id(), requested);
        if (!reservation.requestFingerprint().equals(store.commandFingerprint(current.customerId(), current.tenantId(),
                current.deploymentId(), current.manifest(), exactCommand))
            || !reservation.customerId().equals(current.customerId()) || !reservation.tenantId().equals(current.tenantId())
            || !reservation.deploymentId().equals(current.deploymentId())
            || !reservation.targetProfileId().equals(current.target().targetProfileId())
            || reservation.intent() != current.target().intent() || !reservation.policyId().equals(current.policy().id())
            || !reservation.manifestFingerprint().equals(current.manifestFingerprint())
            || !reservation.bindingFingerprint().equals(current.manifest().bindingFingerprint())
            || reservation.prerequisiteReadback() == null
            || !Objects.equals(reservation.evidenceReference(), reservation.prerequisiteReadback().evidenceReference())) {
            throw unavailable("CUSTOMER_ADMISSION_FINGERPRINT_CHANGED");
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
            || blank(requested.placementEvidenceId()) || requested.operation() == null || blank(requested.operationKey())
            || requested.limits() == null) throw unavailable("CUSTOMER_PREREQUISITE_CONTEXT_INVALID");
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
