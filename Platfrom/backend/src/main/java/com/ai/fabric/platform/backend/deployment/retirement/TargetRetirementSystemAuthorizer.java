package com.ai.fabric.platform.backend.deployment.retirement;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Context;

/**
 * Explicit authorization boundary for service-initiated retirement operations.
 * Implementations must prove authority from durable domain state; an operation-key
 * prefix or a system principal alone is never sufficient.
 */
public interface TargetRetirementSystemAuthorizer {
    boolean supports(Context context);
    /** Returns null only when the exact service operation remains authorized. */
    String blocker(Context context);
}
