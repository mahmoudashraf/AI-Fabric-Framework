package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/**
 * Read-only R7 projection for the R5 managed-dependency lifecycle. It never reserves
 * capacity: the reviewed parent manifest must already contain the complete dependency
 * envelope and an exact dependency-spec evidence reference.
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ManagedDependencyAdmissionLedger {
    public record Requirement(String customerId, String tenantId, String deploymentId,
                              String targetProfileId, String versionId, String bindingFingerprint,
                              String specFingerprint, long cpuMillicores, long memoryBytes,
                              long diskBytes, long databases, long dependencies) { }

    public record Evidence(String reservationId, String generation, long revision, Intent intent,
                           String manifestId, String manifestFingerprint, String bindingFingerprint,
                           Placement placement, String evidenceReference, Instant validUntil) { }

    private final AdmissionStore store;
    private final AdmissionJson json;
    private final Clock clock;

    @Autowired
    public ManagedDependencyAdmissionLedger(AdmissionStore store, AdmissionJson json) {
        this(store, json, Clock.systemUTC());
    }

    ManagedDependencyAdmissionLedger(AdmissionStore store, AdmissionJson json, Clock clock) {
        this.store = store;
        this.json = json;
        this.clock = clock;
    }

    public Evidence require(Requirement requirement, String expectedReservationId) {
        requireInput(requirement);
        var matches = store.held().stream()
            .filter(value -> requirement.customerId().equals(value.customerId()))
            .filter(value -> requirement.tenantId().equals(value.tenantId()))
            .filter(value -> requirement.deploymentId().equals(value.deploymentId()))
            .filter(value -> requirement.targetProfileId().equals(value.targetProfileId()))
            .filter(value -> expectedReservationId == null || expectedReservationId.equals(value.id()))
            .toList();
        if (matches.size() != 1) throw unavailable("DEPENDENCY_ADMISSION_AMBIGUOUS_OR_MISSING");

        Reservation reservation = matches.getFirst();
        Instant now = clock.instant();
        if ((reservation.state() != State.RESERVED && reservation.state() != State.PROVISIONING
                && reservation.state() != State.ACTIVE)
            || !reservation.consumesCapacity() || reservation.expiresAt() == null
            || !reservation.expiresAt().isAfter(now) || reservation.expiryState() != ExpiryState.CURRENT
            || reservation.workerToken() != null || reservation.failureCode() != null) {
            throw unavailable("DEPENDENCY_ADMISSION_INVALID");
        }

        AdmissionContext current = store.context(requirement.customerId(), requirement.tenantId(),
            requirement.deploymentId(), reservation.manifestId());
        policy(current.policy(), now);
        grant(current.grant(), now);
        manifest(current.manifest());
        prerequisites(current, reservation.prerequisiteReadback(), now);

        String specReference = "managed-dependency-spec:" + requirement.specFingerprint();
        if (!reservation.customerId().equals(current.customerId())
            || !reservation.tenantId().equals(current.tenantId())
            || !reservation.deploymentId().equals(current.deploymentId())
            || !reservation.targetProfileId().equals(current.target().targetProfileId())
            || !reservation.policyId().equals(current.policy().id())
            || !reservation.manifestId().equals(current.manifest().id())
            || !reservation.manifestFingerprint().equals(current.manifestFingerprint())
            || !reservation.manifestFingerprint().equals(json.fingerprint(current.manifest()))
            || !reservation.bindingFingerprint().equals(requirement.bindingFingerprint())
            || !current.manifest().bindingFingerprint().equals(requirement.bindingFingerprint())
            || !current.manifest().versionId().equals(requirement.versionId())
            || !current.manifest().prerequisiteReferences().contains(specReference)
            || reservation.prerequisiteReadback() == null
            || !Objects.equals(reservation.evidenceReference(), reservation.prerequisiteReadback().evidenceReference())
            || !reservation.prerequisiteReadback().verifiedPrerequisiteReferences().equals(
                current.manifest().prerequisiteReferences())) {
            throw unavailable("DEPENDENCY_ADMISSION_FINGERPRINT_CHANGED");
        }
        requireEnvelope(current.manifest().demand(), requirement);

        Instant validUntil = minimum(reservation.expiresAt(), reservation.prerequisiteReadback().validUntil(),
            current.grant().validUntil(), current.policy().fleet().validUntil(), current.target().budget().validUntil());
        if (!validUntil.isAfter(now)) throw unavailable("DEPENDENCY_ADMISSION_EXPIRED");
        return new Evidence(reservation.id(), reservation.generation(), reservation.revision(),
            reservation.intent(), reservation.manifestId(), reservation.manifestFingerprint(),
            reservation.bindingFingerprint(), current.target().placement(),
            "admission:" + reservation.id() + ":" + reservation.generation() + ":" + reservation.revision(),
            validUntil);
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
