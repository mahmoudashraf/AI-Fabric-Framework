package com.ai.fabric.platform.backend.deployment.dependency;

/**
 * Target-scoped service verifier for initialization contracts that cannot be
 * established from provider resource health alone. Implementations may call a
 * managed service, but are invoked only outside database command transactions.
 * Successful lifecycle commands require a fresh current-state readback in addition to their idempotent receipt.
 */
public interface ManagedDependencyInitializationVerifier {
    boolean supports(String initializationContract);
    /** Preserve command idempotency and independently verify fresh current readiness for this generation. */
    void initializeAndVerify(ManagedDependencyPrerequisites.Scope scope,
                             String initializationContract,
                             ManagedDependencyPrerequisites.Binding binding,
                             String operationKey);
    void disableAndVerify(ManagedDependencyPrerequisites.Scope scope,
                          String initializationContract,
                          ManagedDependencyPrerequisites.Binding binding);
    void requireUnused(ManagedDependencyPrerequisites.Scope scope,
                       String initializationContract,
                       ManagedDependencyPrerequisites.Binding binding,
                       String retirementOperationKey);
}
