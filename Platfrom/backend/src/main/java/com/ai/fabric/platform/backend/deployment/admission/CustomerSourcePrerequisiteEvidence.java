package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;

import java.time.Instant;
import java.util.Set;

/** A recurring source probe never changes the original admission authorization or capacity allocation. */
record CustomerSourcePrerequisiteEvidence(String id, Scope scope, String placementEvidenceId,
    Set<Prerequisite> passed, String connectorProofDigest, Instant checkedAt, Instant expiresAt) {
    CustomerSourcePrerequisiteEvidence {
        passed = passed == null ? Set.of() : Set.copyOf(passed);
    }

    record Scope(String customerId, String tenantId, String deploymentId, String targetProfileId, String releaseId,
        TargetIntent intent, String offerId, String offerFingerprint, long entitlementRevision,
        String reservationId, String allocationGeneration, String manifestFingerprint, String admissionBindingFingerprint,
        String versionId, String versionConfigHash, String profileFingerprint, String documentBindingFingerprint,
        long storageBindingRevision, String credentialRevisionFingerprint, String runtimeFingerprint) { }
}
