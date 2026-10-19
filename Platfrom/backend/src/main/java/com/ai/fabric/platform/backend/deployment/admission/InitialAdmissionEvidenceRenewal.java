package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext;

import java.time.Instant;
import java.util.Objects;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Append-only relation to an unchanged authorization, stored in the existing reviewed-evidence ledger. */
record InitialAdmissionEvidenceRenewal(String reservationId, String generation,
                                      String authorizationRequestFingerprint, String originalContextFingerprint,
                                      long expectedRevision, long revision, AdmissionContext context,
                                      PrerequisiteReadback readback, String actor, Instant renewedAt) {
    static final String KIND = "INITIAL_PROOF";

    InitialAdmissionEvidenceRenewal {
        if (readback != null && readback.verifiedPrerequisiteReferences() != null) {
            readback = new PrerequisiteReadback(readback.observedAt(), readback.validUntil(), readback.evidenceReference(),
                readback.placement(), readback.profileFingerprint(), readback.manifestFingerprint(), readback.bindingFingerprint(),
                java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(readback.verifiedPrerequisiteReferences())),
                readback.restoreEvidenceReference(), readback.privacyEvidenceReference(), readback.ownerContactEvidenceReference(),
                readback.noticeTransportReference());
        }
    }

    static boolean sameScope(AdmissionContext original, AdmissionContext fresh) {
        if (original == null || fresh == null || original.releaseId() != null || fresh.releaseId() != null
            || original.operation() == null || !java.util.Set.of(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY,
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.PROMOTION_APPLY)
                .contains(original.operation())
            || fresh.placementEvidenceId() == null || fresh.placementEvidenceId().isBlank()) return false;
        var withFreshPlacement = new AdmissionContext(original.customerId(), original.tenantId(), original.deploymentId(),
            original.releaseId(), original.targetProfileId(), original.intent(), original.offerId(), original.offerFingerprint(),
            original.entitlementRevision(), fresh.placementEvidenceId(), original.operation(), original.operationKey(),
            original.limits(), original.topology());
        return Objects.equals(withFreshPlacement, fresh);
    }

    static String key(String authorizationFingerprint, long revision) {
        return authorizationFingerprint + ":" + String.format(java.util.Locale.ROOT, "%020d", revision);
    }
}
