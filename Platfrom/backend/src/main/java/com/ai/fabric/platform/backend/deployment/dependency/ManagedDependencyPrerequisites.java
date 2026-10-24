package com.ai.fabric.platform.backend.deployment.dependency;

import java.util.List;
import java.util.Map;

/** Adapter to core release authorization, target bindings, runtime sync and owned retirement.
 * No default implementation: absent reviewed prerequisites must block provisioning.
 */
public interface ManagedDependencyPrerequisites {
    enum ProvisioningPhase { FOUNDATION, RUNTIME_BOUND }
    record Scope(String graphId, String customerId, String tenantId, String deploymentId,
                 String targetProfileId, String versionId, String releaseId,
                 String specFingerprint, String bindingFingerprint) {
        public Scope(String graphId, String customerId, String tenantId, String deploymentId,
                     String targetProfileId, String versionId, String specFingerprint,
                     String bindingFingerprint) {
            this(graphId, customerId, tenantId, deploymentId, targetProfileId, versionId, null,
                specFingerprint, bindingFingerprint);
        }
    }
    /** Release authority and reviewed bindings. The legacy type name is not a capacity assertion. */
    record Admission(String reservationRef, String bindingFingerprint,
                     Map<String, String> callbacks, List<String> origins,
                     Map<String, String> targetValues, Map<String, String> targetSecretReferences,
                     boolean existingConfiguration, String coreReleaseAuthorizationRef) {
        public Admission(String reservationRef, String bindingFingerprint, Map<String, String> callbacks, List<String> origins,
                         Map<String, String> targetValues, Map<String, String> targetSecretReferences, boolean existingConfiguration) {
            this(reservationRef, bindingFingerprint, callbacks, origins, targetValues, targetSecretReferences,
                existingConfiguration, null);
        }
        public Admission(String reservationRef, String bindingFingerprint, Map<String, String> callbacks, List<String> origins,
                         Map<String, String> targetValues, Map<String, String> targetSecretReferences) {
            this(reservationRef, bindingFingerprint, callbacks, origins, targetValues, targetSecretReferences, false);
        }

        /** A core authorization is not an R7 reservation or evidence that capacity was reserved. */
        public String authorityReference() {
            if (coreReleaseAuthorizationRef != null) {
                if (reservationRef != null || coreReleaseAuthorizationRef.isBlank()) return null;
                return "core-release:" + coreReleaseAuthorizationRef;
            }
            return reservationRef;
        }
    }
    record Binding(String slot, String serviceRef, String baseUrl, String accountId,
                   String bindingId, Map<String, String> secretReferences) {}

    // Revalidate the exact release/spec/owner authority. New releases carry core authorization;
    // the reservation field is populated only for an existing historical R7-backed graph.
    Admission reserve(Scope scope, ManagedDependencySpec spec, ProvisioningPhase phase);
    void validateReservation(Scope scope, String reservationRef);
    // Provider-effect checkpoints refresh real placement outside transactions; metadata projection stays read-only.
    default void validateExecutionReservation(Scope scope, String reservationRef) { validateReservation(scope, reservationRef); }
    // Must verify reviewed artifact provenance, availability and exact digest, not only syntax.
    void validateArtifact(Scope scope, ManagedDependencySpec.Service service);
    // Idempotent, account/target-scoped seed (never fixture-wide reset), sync and canary readback.
    // Reapply must preserve fixture state. Returns only after callback registration and canaries succeed.
    void initializeAndVerify(Scope scope, String initializationContract, Binding binding,
                             String operationKey);
    // Stop admitting human work, resolve pending callbacks/work and verify this binding is disabled.
    void disableAndVerifyBinding(Scope scope, Binding binding);
    // Removal-only proof of the frozen inbox scope; must not require fresh provisioning/admission evidence.
    void validateOperatorRevocationScope(Scope scope, Binding binding);
    void verifyOperatorRevocation(Scope scope, Binding binding, List<String> retiredCredentialReferences,
                                  String operationKey, boolean disabled);
    // Must prove the prior execution process is terminated, not just that a heartbeat is old.
    void requireInterruptedWorkerTerminated(Scope scope, String workerToken);
    // Validates current assignments/generations, resolved human work, backup/retention and no references.
    // Called immediately before every destructive action. Must serialize against activation in R3.
    void requireUnused(Scope scope, long graphRevision, String retirementOperationKey);
    // Confirm physical database/volume reclamation, not just absent application metadata.
    void verifyReclaimed(Scope scope, List<ManagedDependencyResource> resources);
    void releaseReservation(Scope scope, String reservationRef);
}
