package com.ai.fabric.platform.backend.customerselfservice.model;

import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Retention;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Step;

import java.time.Instant;
import java.util.List;

/** Consumer identities and assignment revisions are always derived by the server. */
public final class CustomerTargetRetirementContracts {
    private CustomerTargetRetirementContracts() { }

    public record PlanRequest(String targetProfileId, List<String> releaseIds, Instant drainDeadline,
                              Retention retention, String operationKey, String reason) { }

    public record Command(String requestFingerprint) { }

    public record Summary(String id, String deploymentId, String targetProfileId, String requestFingerprint,
                          State state, Step step, Instant drainDeadline, Retention retention,
                          int attempt, Instant nextRetryAt, String errorCode, List<String> blockers,
                          boolean consumesCapacity, boolean customerCommandsAllowed,
                          Instant createdAt, Instant startedAt,
                          Instant completedAt, Instant updatedAt) { }
}
