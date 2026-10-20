package com.ai.fabric.platform.backend.deployment.admission;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Append-only authority for a held envelope, separate from both its original policy and fresh measurements. */
record AdmissionPolicyContinuity(String originalPolicyId, String originalPolicyFingerprint,
    String measuredPolicyId, String measuredPolicyFingerprint, String previousMeasuredPolicyId,
    String previousMeasuredPolicyFingerprint, Fence fence, String reservationFingerprint,
    String allocationIdentity, Set<String> authorizedPolicyReferences, Map<String, String> placementEvidence,
    String actor, Instant reviewedAt) {
    static final String KIND = "POLICY_CONT";

    AdmissionPolicyContinuity {
        authorizedPolicyReferences = Collections.unmodifiableSet(new TreeSet<>(authorizedPolicyReferences));
        placementEvidence = Map.copyOf(placementEvidence);
    }

    static String key(AdmissionJson json, String policyId, String reservationId) {
        return json.fingerprint(Map.of("policy", policyId, "reservation", reservationId));
    }

    static String identity(AdmissionJson json, Reservation reservation) {
        var values = new java.util.TreeMap<String, Object>();
        values.put("id", reservation.id()); values.put("generation", reservation.generation());
        values.put("customer", reservation.customerId()); values.put("tenant", reservation.tenantId());
        values.put("deployment", reservation.deploymentId()); values.put("target", reservation.targetProfileId());
        values.put("intent", reservation.intent()); values.put("policy", reservation.policyId());
        values.put("operationKey", reservation.operationKey()); values.put("requestFingerprint", reservation.requestFingerprint());
        values.put("demand", reservation.demand()); values.put("createdAt", reservation.createdAt());
        values.put("customerContextFingerprint", reservation.customerContextFingerprint());
        values.put("customerOperation", reservation.customerOperation());
        return json.fingerprint(values);
    }

    static void compatible(FleetPolicy original, FleetPolicy measured) {
        if (original.id().equals(measured.id()) || !original.lifecycle().equals(measured.lifecycle())
            || !original.restoreEvidenceReference().equals(measured.restoreEvidenceReference())
            || !original.privacyEvidenceReference().equals(measured.privacyEvidenceReference())
            || !original.supportReference().equals(measured.supportReference())
            || !original.targets().keySet().equals(measured.targets().keySet())) deny();
        // Fleet measurementReference is the offer's opaque compatibility anchor, not a target report identity.
        capacityContract(original.fleet(), measured.fleet(), false);
        original.targets().forEach((id, before) -> {
            TargetBudget after = measured.targets().get(id);
            if (!before.targetProfileId().equals(after.targetProfileId()) || before.intent() != after.intent()
                || !before.placement().equals(after.placement()) || !before.profileFingerprint().equals(after.profileFingerprint())) deny();
            capacityContract(before.budget(), after.budget(), true);
        });
    }

    private static void capacityContract(Budget before, Budget after, boolean reportIdentity) {
        // Baseline is remeasured; approved ceilings and safety headroom are not enlarged by continuity.
        if (!before.measured().equals(after.measured()) || !before.headroom().equals(after.headroom())
            || !after.measuredAt().isAfter(before.measuredAt())
            || (!reportIdentity && !before.measurementReference().equals(after.measurementReference()))
            || (reportIdentity && before.measurementReference().equals(after.measurementReference()))) deny();
    }

    private static void deny() { throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_INCOMPATIBLE"); }
}
