package com.ai.fabric.platform.backend.deployment.admission;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.*;

final class AdmissionRules {
    private AdmissionRules() { }
    static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim()))
            throw new AdmissionFailure(BAD_REQUEST, "ADMISSION_INPUT_INVALID");
        return value;
    }
    static void vector(Vector vector) {
        if (vector == null || vector.amounts() == null || vector.amounts().size() != Dimension.values().length)
            throw new AdmissionFailure(PRECONDITION_FAILED, "COMPLETE_RESOURCE_ENVELOPE_REQUIRED");
        for (Dimension dimension : Dimension.values()) {
            Long amount = vector.amounts().get(dimension);
            if (amount == null || amount < 0) throw new AdmissionFailure(PRECONDITION_FAILED, "COMPLETE_RESOURCE_ENVELOPE_REQUIRED");
        }
    }
    static Vector zero() {
        EnumMap<Dimension, Long> values = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) values.put(dimension, 0L);
        return new Vector(values);
    }
    static Vector sum(List<Vector> vectors) {
        EnumMap<Dimension, Long> result = new EnumMap<>(zero().amounts());
        try {
            for (Vector vector : vectors) {
                vector(vector);
                for (Dimension dimension : Dimension.values())
                    result.put(dimension, Math.addExact(result.get(dimension), vector.amounts().get(dimension)));
            }
        } catch (ArithmeticException ex) { throw new AdmissionFailure(CONFLICT, "CAPACITY_ARITHMETIC_OVERFLOW"); }
        return new Vector(result);
    }
    static void within(Vector used, Vector limit, String code) {
        vector(limit);
        vector(used);
        for (Dimension dimension : Dimension.values())
            if (used.amounts().get(dimension) > limit.amounts().get(dimension))
                throw new AdmissionFailure(CONFLICT, code + ":" + dimension.name());
    }
    static void budget(Budget budget, Instant now) {
        if (budget == null || budget.measuredAt() == null || budget.validUntil() == null
            || budget.measuredAt().isAfter(now) || !budget.validUntil().isAfter(now)
            || !budget.validUntil().isAfter(budget.measuredAt()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "FRESH_MEASURED_CAPACITY_REQUIRED");
        required(budget.measurementReference(), 255);
        vector(budget.measured()); vector(budget.baseline()); vector(budget.headroom());
        for (Dimension d : List.of(Dimension.CPU_MILLICORES, Dimension.MEMORY_BYTES, Dimension.DISK_BYTES))
            if (budget.measured().amounts().get(d) == 0) throw new AdmissionFailure(PRECONDITION_FAILED, "MEASURED_CPU_RAM_DISK_REQUIRED");
        within(sum(List.of(budget.baseline(), budget.headroom())), budget.measured(), "INVALID_HEADROOM");
    }
    static void placement(Intent intent, Placement placement) {
        Placement expected = switch (Objects.requireNonNull(intent)) {
            case CUSTOMER_STAGING -> new Placement("zf25hgk9694bt7q0zwb98ado", "xjhfu65nacrr30xax5cp0ry7", "46.224.145.148");
            case CUSTOMER_PRODUCTION -> new Placement("kvufjk78dj4wyhjgp1mlxecr", "r3thf2xmxcjn1tt2bclabebz", "46.225.162.106");
        };
        if (!expected.equals(placement)) throw new AdmissionFailure(PRECONDITION_FAILED, "PHYSICAL_PLACEMENT_FORBIDDEN");
    }
    static void lifecycle(LifecyclePolicy policy) {
        if (policy == null || policy.inactivitySeconds() <= 0 || policy.noticeLeadSeconds() <= 0
            || policy.noticeLeadSeconds() >= policy.inactivitySeconds() || policy.extensionSeconds() <= 0
            || policy.maxExtensions() < 0 || policy.maxLifetimeSeconds() < policy.inactivitySeconds()
            || policy.maxKeepSeconds() <= 0 || policy.drainSeconds() <= 0 || policy.recoverySeconds() <= 0
            || policy.retrySeconds() <= 0 || policy.maxAttempts() <= 0 || policy.readbackMaxAgeSeconds() <= 0
            || policy.budgetWindowSeconds() <= 0)
            throw new AdmissionFailure(PRECONDITION_FAILED, "EXPLICIT_LIFECYCLE_POLICY_REQUIRED");
        required(policy.policyReference(), 255); required(policy.noticeTransportReference(), 255);
    }
    static void policy(FleetPolicy policy, Instant now) {
        if (policy == null || policy.targets() == null || policy.targets().isEmpty() || policy.targets().size() > 32)
            throw new AdmissionFailure(PRECONDITION_FAILED, "MEASURED_POLICY_REQUIRED");
        required(policy.id(), 64); budget(policy.fleet(), now); lifecycle(policy.lifecycle());
        required(policy.restoreEvidenceReference(), 255); required(policy.privacyEvidenceReference(), 255);
        required(policy.supportReference(), 255);
        policy.targets().forEach((key, target) -> {
            if (target == null || !key.equals(target.targetProfileId()) || target.intent() == null
                || "dtp-coolify-prod-staging".equals(key)) throw new AdmissionFailure(PRECONDITION_FAILED, "TARGET_POLICY_INVALID");
            required(key, 64); required(target.profileFingerprint(), 64);
            placement(target.intent(), target.placement()); budget(target.budget(), now);
        });
        // Aliases on one physical server cannot create independent capacity pools.
        if (policy.targets().values().stream().map(t -> t.placement().serverUuid()).distinct().count() != policy.targets().size())
            throw new AdmissionFailure(PRECONDITION_FAILED, "DUPLICATE_PHYSICAL_CAPACITY_POOL");
    }
    static void grant(CustomerGrant grant, Instant now) {
        if (grant == null || grant.validUntil() == null || !grant.validUntil().isAfter(now)
            || grant.targetLimits() == null || grant.targetLimits().isEmpty())
            throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_CUSTOMER_QUOTA_REQUIRED");
        required(grant.customerId(), 64); vector(grant.fleetLimit());
        grant.targetLimits().forEach((id, v) -> { required(id, 64); vector(v); });
        required(grant.ownerContactEvidenceReference(), 255); required(grant.payerReference(), 255);
        if (grant.keepStagingEntitlementReference() != null) required(grant.keepStagingEntitlementReference(), 255);
    }
    static void manifest(Manifest manifest) {
        if (manifest == null || manifest.prerequisiteReferences() == null || manifest.prerequisiteReferences().isEmpty()
            || manifest.prerequisiteReferences().size() > 64) throw new AdmissionFailure(PRECONDITION_FAILED, "REVIEWED_MANIFEST_REQUIRED");
        required(manifest.id(), 64); required(manifest.deploymentId(), 64); required(manifest.versionId(), 64);
        required(manifest.versionConfigHash(), 255); required(manifest.targetProfileId(), 64);
        required(manifest.bindingFingerprint(), 64); required(manifest.topologyReference(), 255);
        manifest.prerequisiteReferences().forEach(ref -> required(ref, 255)); vector(manifest.demand());
        if (manifest.customerPrerequisiteReferences() == null) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_PREREQUISITE_MAP_INVALID");
        }
        manifest.customerPrerequisiteReferences().forEach((prerequisite, reference) -> {
            if (prerequisite == null || prerequisite == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite.CAPACITY
                || reference == null || !manifest.prerequisiteReferences().contains(reference)) {
                throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_PREREQUISITE_MAP_INVALID");
            }
            required(reference, 255);
        });
        if (manifest.demand().amounts().get(Dimension.ENVIRONMENTS) != 1
            || manifest.demand().amounts().get(Dimension.REPLICAS) < 1
            || manifest.demand().amounts().get(Dimension.CPU_MILLICORES) < 1
            || manifest.demand().amounts().get(Dimension.MEMORY_BYTES) < 1
            || manifest.demand().amounts().get(Dimension.DISK_BYTES) < 1)
            throw new AdmissionFailure(PRECONDITION_FAILED, "FULL_TOPOLOGY_ENVELOPE_REQUIRED");
    }
    static void fresh(Instant observed, Instant now, long maxAge) {
        if (observed == null || observed.isAfter(now) || observed.isBefore(now.minusSeconds(maxAge)))
            throw new AdmissionFailure(PRECONDITION_FAILED, "FRESH_READBACK_REQUIRED");
    }
    static void prerequisites(AdmissionContext context, PrerequisiteReadback evidence, Instant now) {
        if (evidence == null) throw new AdmissionFailure(PRECONDITION_FAILED, "PREREQUISITE_READBACK_REQUIRED");
        fresh(evidence.observedAt(), now, context.policy().lifecycle().readbackMaxAgeSeconds());
        required(evidence.evidenceReference(), 255);
        if (evidence.validUntil() == null || !evidence.validUntil().isAfter(now)
            || !context.target().placement().equals(evidence.placement())
            || !context.target().profileFingerprint().equals(evidence.profileFingerprint())
            || !context.manifestFingerprint().equals(evidence.manifestFingerprint())
            || !context.manifest().bindingFingerprint().equals(evidence.bindingFingerprint())
            || evidence.verifiedPrerequisiteReferences() == null
            || !evidence.verifiedPrerequisiteReferences().containsAll(context.manifest().prerequisiteReferences())
            || !context.policy().restoreEvidenceReference().equals(evidence.restoreEvidenceReference())
            || !context.policy().privacyEvidenceReference().equals(evidence.privacyEvidenceReference())
            || !context.grant().ownerContactEvidenceReference().equals(evidence.ownerContactEvidenceReference())
            || !context.policy().lifecycle().noticeTransportReference().equals(evidence.noticeTransportReference()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "ADMISSION_PREREQUISITE_UNVERIFIED");
    }
    static void releaseReadback(ReadbackContext context, ResourceReadback evidence, Instant now) {
        reclamationReadback(context, evidence, now, true);
        if (evidence.retainedIndex() != null) throw new AdmissionFailure(PRECONDITION_FAILED, "RETAINED_INDEX_CAPACITY_REQUIRED");
    }
    static void retainedReadback(ReadbackContext context, ResourceReadback evidence, Instant now) {
        reclamationReadback(context, evidence, now, false);
        if (evidence.retainedIndex() == null) throw new AdmissionFailure(PRECONDITION_FAILED, "RETENTION_RECEIPT_REQUIRED");
    }
    private static void reclamationReadback(ReadbackContext context, ResourceReadback evidence, Instant now, boolean absent) {
        Reservation r = context.reservation();
        if (evidence == null) throw new AdmissionFailure(PRECONDITION_FAILED, "RESOURCE_READBACK_REQUIRED");
        fresh(evidence.observedAt(), now, context.policy().lifecycle().readbackMaxAgeSeconds());
        required(evidence.evidenceReference(), 255);
        resourceIdentity(context, evidence);
        if (r.recoveryUntil() != null && now.isBefore(r.recoveryUntil()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "RECOVERY_WINDOW_NOT_ELAPSED");
        if (!evidence.inventoryComplete() || !evidence.outcomeSettled() || evidence.resourcesAbsent() != absent
            || !evidence.ingressBlocked() || !evidence.retentionVerified() || !evidence.assignmentsVerified()
            || evidence.assignmentCount() != 0 || !isZero(evidence.sessions()) || !isZero(evidence.confirmations())
            || !isZero(evidence.humanWork()) || !isZero(evidence.backgroundWork()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "RECLAMATION_UNPROVEN");
    }
    static void resourceIdentity(ReadbackContext context, ResourceReadback evidence) {
        Reservation r = context.reservation();
        TargetBudget target = context.policy().targets().get(r.targetProfileId());
        if (!r.id().equals(evidence.reservationId()) || !r.generation().equals(evidence.generation())
            || r.revision() != evidence.expectedRevision() || !Objects.equals(context.workerToken(), evidence.workerToken())
            || !r.manifestFingerprint().equals(evidence.manifestFingerprint())
            || !r.bindingFingerprint().equals(evidence.bindingFingerprint()) || target == null
            || !target.placement().equals(evidence.placement()))
            throw new AdmissionFailure(CONFLICT, "STALE_RESOURCE_GENERATION");
    }
    private static boolean isZero(Long value) { return value != null && value == 0; }
}
