package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** A new reviewed release may replace known native resources; the failed release never becomes verified. */
final class ProvisionedVerificationReapplyEvidence {
    record Proof(Fence fence, ReleaseBinding binding, String releaseFingerprint, ResourceReadback resources) { }

    private final JdbcTemplate jdbc;
    private final AdmissionJson json;

    ProvisionedVerificationReapplyEvidence(JdbcTemplate jdbc, AdmissionJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Proof capture(Reservation allocation, ReleaseBinding binding, Manifest manifest, ResourceReadback resources,
                  FleetPolicy policy, Instant now) {
        return checked(allocation, binding, manifest, resources, policy, now);
    }

    void validate(Reservation allocation, ReleaseBinding binding, Manifest manifest, Proof proof,
                  FleetPolicy policy, Instant now) {
        if (proof == null || binding == null || !allocation.fence().equals(proof.fence()) || !binding.equals(proof.binding())) throw denied();
        if (!proof.equals(checked(allocation, binding, manifest, proof.resources(), policy, now))) throw denied();
    }

    private Proof checked(Reservation allocation, ReleaseBinding binding, Manifest manifest, ResourceReadback resources,
                          FleetPolicy policy, Instant now) {
        if (allocation.state() != State.OUTCOME_UNKNOWN || allocation.workerToken() != null
            || allocation.expiryState() != ExpiryState.CURRENT || !allocation.expiresAt().isAfter(now)
            || binding == null || !"OUTCOME_UNKNOWN".equals(binding.state())
            || !allocation.id().equals(binding.reservationId()) || !allocation.generation().equals(binding.generation())
            || !allocation.customerId().equals(binding.customerId()) || !allocation.tenantId().equals(binding.tenantId())
            || !allocation.deploymentId().equals(binding.deploymentId()) || !allocation.targetProfileId().equals(binding.targetProfileId())
            || !manifest.versionId().equals(binding.versionId()) || !manifest.versionConfigHash().equals(binding.versionConfigHash())
            || resources == null || !resources.inventoryComplete() || !resources.outcomeSettled()
            || resources.resourcesAbsent() || resources.assignmentCount() != 0) throw denied();
        fresh(resources.observedAt(), now, policy.lifecycle().readbackMaxAgeSeconds());
        resourceIdentity(new ReadbackContext(allocation, policy, null), resources);
        required(resources.evidenceReference(), 255);
        var rows = jdbc.queryForList("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE", binding.releaseId());
        if (rows.size() != 1) throw denied();
        var release = rows.getFirst();
        if (!allocation.deploymentId().equals(release.get("deployment_id"))
            || !allocation.targetProfileId().equals(release.get("target_profile_id"))
            || !manifest.versionId().equals(release.get("deployment_version_id"))
            || !"COOLIFY".equals(release.get("provider_type"))
            || !"APPLIED_VERIFICATION_FAILED".equals(release.get("status"))
            || !"ACTIVE".equals(release.get("provisioning_status"))
            || !"FAILED".equals(release.get("verification_status"))
            || release.get("provider_resource_handle_id") == null
            || !(release.get("runtime_authority_fingerprint") instanceof String authority)
            || !authority.matches("[a-f0-9]{64}")) throw denied();
        Long assigned = jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_consumers WHERE bound_release_id=?
               OR (bound_deployment_id=? AND (bound_target_profile_id=? OR bound_target_profile_id IS NULL)
                  AND (bound_release_id IS NOT NULL OR bound_target_profile_id IS NULL))
            """, Long.class, binding.releaseId(), allocation.deploymentId(), allocation.targetProfileId());
        if (assigned == null || assigned != 0) throw denied();
        return new Proof(allocation.fence(), binding, json.fingerprint(release), resources);
    }

    private AdmissionFailure denied() { return new AdmissionFailure(CONFLICT, "PROVISIONED_VERIFICATION_REAPPLY_UNPROVEN"); }
}
