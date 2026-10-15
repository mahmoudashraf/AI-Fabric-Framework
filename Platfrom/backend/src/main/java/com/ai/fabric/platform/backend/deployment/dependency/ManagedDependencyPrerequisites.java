package com.ai.fabric.platform.backend.deployment.dependency;

import java.util.List;
import java.util.Map;

/** Adapter to existing template admission, target bindings, runtime sync and R3 drain policy.
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
    record Admission(String reservationRef, String bindingFingerprint,
                     Map<String, String> callbacks, List<String> origins,
                     Map<String, String> targetValues, Map<String, String> targetSecretReferences) {}
    record Binding(String slot, String serviceRef, String baseUrl, String accountId,
                   String bindingId, Map<String, String> secretReferences) {}

    // Must atomically reserve the complete graph, include builds/concurrent work, and revalidate entitlement.
    // Repeated graphId + fingerprint returns the same durable reservation; never manufacture capacity.
    Admission reserve(Scope scope, ManagedDependencySpec spec, ProvisioningPhase phase);
    void validateReservation(Scope scope, String reservationRef);
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
    void verifyReclaimed(Scope scope, List<ManagedDependencyRetirementAdapter.Resource> resources);
    void releaseReservation(Scope scope, String reservationRef);
}
