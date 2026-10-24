package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/**
 * Read-only R7 projection for the R5 managed-dependency lifecycle. It never reserves
 * capacity: the reviewed parent manifest must already contain the complete dependency
 * envelope and an exact dependency-spec evidence reference.
 */
@Service
public class ManagedDependencyAdmissionLedger {
    public record Requirement(String customerId, String tenantId, String deploymentId,
                              String targetProfileId, String versionId, String bindingFingerprint,
                              String specFingerprint, long cpuMillicores, long memoryBytes,
                              long diskBytes, long databases, long dependencies) { }

    public record Evidence(String reservationId, String generation, long revision, Intent intent,
                           String manifestId, String manifestFingerprint, String bindingFingerprint,
                           Placement placement, String evidenceReference, Instant validUntil, boolean existingConfiguration) {
        public Evidence(String reservationId, String generation, long revision, Intent intent, String manifestId,
                        String manifestFingerprint, String bindingFingerprint, Placement placement, String evidenceReference, Instant validUntil) {
            this(reservationId, generation, revision, intent, manifestId, manifestFingerprint, bindingFingerprint,
                placement, evidenceReference, validUntil, false);
        }
    }

    private final AdmissionStore store;
    private final AdmissionJson json;
    private final AdmissionEvidenceSource prerequisites;
    private final Clock clock;

    @Autowired
    public ManagedDependencyAdmissionLedger(AdmissionStore store, AdmissionJson json, AdmissionEvidenceSource prerequisites) {
        this(store, json, prerequisites, Clock.systemUTC());
    }

    ManagedDependencyAdmissionLedger(AdmissionStore store, AdmissionJson json, AdmissionEvidenceSource prerequisites, Clock clock) {
        this.store = store;
        this.json = json;
        this.prerequisites = prerequisites;
        this.clock = clock;
    }

    public Evidence require(Requirement requirement, String expectedReservationId) {
        return require(requirement, expectedReservationId, false);
    }

    /** Only an explicit worker boundary may obtain provider evidence; never suspend a caller's locks. */
    @Transactional(propagation = Propagation.NEVER)
    public Evidence requireForExecution(Requirement requirement, String expectedReservationId) {
        return require(requirement, expectedReservationId, true);
    }

    private Evidence require(Requirement requirement, String expectedReservationId, boolean execution) {
        return require(requirement, expectedReservationId, execution, null);
    }

    /** Classification only; the selected admission path must still validate all execution evidence. */
    public Intent reviewedIntent(Requirement requirement, String reservationId) {
        requireInput(requirement);
        var reservation = reservation(requirement, required(reservationId, 64));
        var current = store.context(reservation);
        if (!reservation.targetProfileId().equals(current.target().targetProfileId())
            || reservation.intent() != current.target().intent())
            throw unavailable("DEPENDENCY_ADMISSION_FINGERPRINT_CHANGED");
        return current.target().intent();
    }

    public Evidence requireConfiguration(Requirement requirement, String reservationId, String releaseId) {
        return require(requirement, required(reservationId, 64), false, required(releaseId, 64));
    }

    @Transactional(propagation = Propagation.NEVER)
    public Evidence requireConfigurationForExecution(Requirement requirement, String reservationId, String releaseId) {
        return require(requirement, required(reservationId, 64), true, required(releaseId, 64));
    }

    private Evidence require(Requirement requirement, String expectedReservationId, boolean execution, String releaseId) {
        requireInput(requirement);
        Reservation reservation = reservation(requirement, expectedReservationId);
        boolean existingConfiguration = releaseId != null;
        if (existingConfiguration && reservation.intent() != Intent.CUSTOMER_PRODUCTION)
            throw unavailable("DEPENDENCY_ACTIVE_PRODUCTION_REQUIRED");
        AdmissionContext inspected = validate(requirement, reservation, clock.instant(), existingConfiguration, releaseId);
        PrerequisiteReadback readback = existingConfiguration
            ? execution ? prerequisites.readExistingConfigurationForExecution(inspected) : prerequisites.readExistingConfiguration(inspected)
            : execution ? prerequisites.readForExecution(inspected) : prerequisites.read(inspected);
        Reservation current = reservation(requirement, expectedReservationId);
        AdmissionContext context = validate(requirement, current, clock.instant(), existingConfiguration, releaseId);
        if (!reservation.equals(current) || !inspected.equals(context))
            throw unavailable("DEPENDENCY_ADMISSION_CHANGED_DURING_READBACK");
        Instant now = clock.instant();
        prerequisites(context, readback, now);
        if (!context.manifest().prerequisiteReferences().equals(readback.verifiedPrerequisiteReferences()))
            throw unavailable("DEPENDENCY_ADMISSION_FINGERPRINT_CHANGED");

        Instant validUntil = existingConfiguration ? minimum(readback.validUntil(), context.grant().validUntil(),
            readback.observedAt().plusSeconds(context.policy().lifecycle().readbackMaxAgeSeconds())) : minimum(current.expiresAt(), readback.validUntil(),
            readback.observedAt().plusSeconds(context.policy().lifecycle().readbackMaxAgeSeconds()), context.grant().validUntil(),
            context.policy().fleet().validUntil(), context.target().budget().validUntil());
        if (!validUntil.isAfter(now)) throw unavailable("DEPENDENCY_ADMISSION_EXPIRED");
        return new Evidence(current.id(), current.generation(), current.revision(),
            current.intent(), current.manifestId(), current.manifestFingerprint(),
            current.bindingFingerprint(), context.target().placement(),
            "admission:" + current.id() + ":" + current.generation() + ":" + current.revision(), validUntil, existingConfiguration);
    }

    private Reservation reservation(Requirement requirement, String expectedReservationId) {
        var matches = store.held().stream()
            .filter(Reservation::occupiesActiveSlot)
            .filter(value -> requirement.customerId().equals(value.customerId()))
            .filter(value -> requirement.tenantId().equals(value.tenantId()))
            .filter(value -> requirement.deploymentId().equals(value.deploymentId()))
            .filter(value -> requirement.targetProfileId().equals(value.targetProfileId()))
            .filter(value -> expectedReservationId == null || expectedReservationId.equals(value.id()))
            .toList();
        if (matches.size() != 1) throw unavailable("DEPENDENCY_ADMISSION_AMBIGUOUS_OR_MISSING");
        return matches.getFirst();
    }

    private AdmissionContext validate(Requirement requirement, Reservation reservation, Instant now,
                                      boolean existingConfiguration, String releaseId) {
        if ((reservation.state() != State.RESERVED && reservation.state() != State.PROVISIONING
                && reservation.state() != State.ACTIVE)
            || !reservation.consumesCapacity() || reservation.expiresAt() == null
            || (!existingConfiguration && !reservation.expiresAt().isAfter(now)) || reservation.expiryState() != ExpiryState.CURRENT
            || reservation.workerToken() != null || reservation.failureCode() != null) {
            throw unavailable("DEPENDENCY_ADMISSION_INVALID");
        }

        AdmissionContext current = store.context(reservation);
        if (existingConfiguration) {
            ReleaseBinding release = store.releaseBinding(releaseId);
            if (reservation.state() != State.ACTIVE || reservation.intent() != Intent.CUSTOMER_PRODUCTION
                || reservation.retirementOperationId() != null || release == null || !"ACTIVE".equals(release.state())
                || !releaseId.equals(store.currentReleaseId(reservation)) || !reservation.id().equals(release.reservationId())
                || !reservation.generation().equals(release.generation()) || !requirement.customerId().equals(release.customerId())
                || !requirement.tenantId().equals(release.tenantId()) || !requirement.deploymentId().equals(release.deploymentId())
                || !requirement.targetProfileId().equals(release.targetProfileId()) || !requirement.versionId().equals(release.versionId())
                || !current.manifest().versionConfigHash().equals(release.versionConfigHash()))
                throw unavailable("DEPENDENCY_ACTIVE_PRODUCTION_REQUIRED");
            historicalPolicy(reservation, current);
        } else policy(current.policy(), now);
        grant(current.grant(), now);
        manifest(current.manifest());

        String specReference = "managed-dependency-spec:" + requirement.specFingerprint();
        if (!reservation.customerId().equals(current.customerId())
            || !reservation.tenantId().equals(current.tenantId())
            || !reservation.deploymentId().equals(current.deploymentId())
            || !reservation.targetProfileId().equals(current.target().targetProfileId())
            || reservation.intent() != current.target().intent()
            || !reservation.manifestId().equals(current.manifest().id())
            || !reservation.manifestFingerprint().equals(current.manifestFingerprint())
            || !reservation.manifestFingerprint().equals(json.fingerprint(current.manifest()))
            || !reservation.bindingFingerprint().equals(requirement.bindingFingerprint())
            || !current.manifest().bindingFingerprint().equals(requirement.bindingFingerprint())
            || !current.manifest().versionId().equals(requirement.versionId())
            || !current.manifest().prerequisiteReferences().contains(specReference)
            || reservation.prerequisiteReadback() == null
            || !current.target().placement().equals(reservation.prerequisiteReadback().placement())
            || !current.target().profileFingerprint().equals(reservation.prerequisiteReadback().profileFingerprint())
            || !current.manifestFingerprint().equals(reservation.prerequisiteReadback().manifestFingerprint())
            || !current.manifest().bindingFingerprint().equals(reservation.prerequisiteReadback().bindingFingerprint())
            || !current.manifest().prerequisiteReferences().equals(
                reservation.prerequisiteReadback().verifiedPrerequisiteReferences())) {
            throw unavailable("DEPENDENCY_ADMISSION_FINGERPRINT_CHANGED");
        }
        requireEnvelope(current.manifest().demand(), requirement);
        return current;
    }

    private void requireEnvelope(Vector demand, Requirement requirement) {
        Map<Dimension, Long> amounts = demand == null ? null : demand.amounts();
        if (amounts == null
            || below(amounts, Dimension.CPU_MILLICORES, requirement.cpuMillicores())
            || below(amounts, Dimension.MEMORY_BYTES, requirement.memoryBytes())
            || below(amounts, Dimension.DISK_BYTES, requirement.diskBytes())
            || below(amounts, Dimension.DATABASES, requirement.databases())
            || below(amounts, Dimension.DEPENDENCIES, requirement.dependencies())) {
            throw unavailable("DEPENDENCY_CAPACITY_NOT_RESERVED");
        }
    }

    private boolean below(Map<Dimension, Long> amounts, Dimension dimension, long required) {
        Long actual = amounts.get(dimension);
        return actual == null || actual < required;
    }

    private void requireInput(Requirement value) {
        if (value == null || blank(value.customerId()) || blank(value.tenantId())
            || blank(value.deploymentId()) || blank(value.targetProfileId()) || blank(value.versionId())
            || value.bindingFingerprint() == null || !value.bindingFingerprint().matches("[a-f0-9]{64}")
            || value.specFingerprint() == null || !value.specFingerprint().matches("[a-f0-9]{64}")
            || value.cpuMillicores() < 0 || value.memoryBytes() < 0 || value.diskBytes() < 0
            || value.databases() < 0 || value.dependencies() < 0) {
            throw unavailable("DEPENDENCY_ADMISSION_CONTEXT_INVALID");
        }
    }

    private Instant minimum(Instant first, Instant... rest) {
        if (first == null) throw unavailable("DEPENDENCY_ADMISSION_EVIDENCE_UNKNOWN");
        Instant result = first;
        for (Instant candidate : rest) {
            if (candidate == null) throw unavailable("DEPENDENCY_ADMISSION_EVIDENCE_UNKNOWN");
            if (candidate.isBefore(result)) result = candidate;
        }
        return result;
    }

    private AdmissionFailure unavailable(String code) {
        return new AdmissionFailure(PRECONDITION_FAILED, code);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
