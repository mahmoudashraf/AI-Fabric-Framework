package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

/** Wire types are intentionally independent of the optional service's Java artifact. */
public final class LifecycleContracts {
    private LifecycleContracts() { }
    public static final String ID = "[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}";
    public enum Goal { STOP, RETIRE, RECOVER, RECONCILE }
    public enum Mode { COLD, GRACEFUL }
    public enum Phase { CLAIM, BEGIN_DRAIN, OBSERVE_DRAIN, FENCE, STOP, DELETE, RECOVER, VERIFY, RELEASE, CANCEL, OBSERVE }
    public enum Status { PENDING, SUCCEEDED, BLOCKED, UNKNOWN, FAILED }

    public record JobRequest(String schemaVersion, String operationId, String grantId, String customerId,
        String tenantId, String deploymentId, String targetProfileId, String releaseId, String scopeHash,
        Goal goal, Mode mode, String policyRevision, Instant dueAt, Instant deadlineAt, Instant requestedAt) { }

    public record Command(
        @NotNull @Pattern(regexp = "1") String schemaVersion,
        @NotNull @Pattern(regexp = ID) String commandId,
        @NotNull @Pattern(regexp = ID) String operationId,
        @NotNull @Pattern(regexp = ID) String grantId,
        @NotNull @Pattern(regexp = "[a-f0-9]{64}") String scopeHash,
        @NotNull Phase phase) { }

    public record Receipt(String schemaVersion, String commandId, String operationId, String grantId,
        Phase phase, Status status, String code, Instant observedAt, List<String> blockers) { }

    public record CreateJob(
        @NotNull @Pattern(regexp = ID) String operationId,
        @NotNull @Pattern(regexp = ID) String releaseId,
        @NotNull Goal goal, @NotNull Mode mode,
        @NotNull @Pattern(regexp = ID) String policyRevision,
        @NotNull Instant dueAt, @NotNull Instant deadlineAt, boolean reclaimOwnedData) { }
}
