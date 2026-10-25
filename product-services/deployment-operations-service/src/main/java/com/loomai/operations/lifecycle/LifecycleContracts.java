package com.loomai.operations.lifecycle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Version 1 wire boundary mirrored by core. Grants, not these caller fields, confer authority. */
public final class LifecycleContracts {
    private LifecycleContracts() { }

    public static final String ID = "[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}";
    public static final String SHA256 = "[a-fA-F0-9]{64}";

    public enum Goal { STOP, RETIRE, RECOVER, RECONCILE }
    public enum Mode { COLD, GRACEFUL }
    public enum Phase {
        CLAIM, BEGIN_DRAIN, OBSERVE_DRAIN, FENCE, STOP, DELETE, RECOVER, VERIFY, RELEASE, CANCEL, OBSERVE
    }
    public enum Status { PENDING, SUCCEEDED, BLOCKED, UNKNOWN, FAILED }

    public record JobRequest(
        @NotNull @Pattern(regexp = "1") String schemaVersion,
        @NotNull @Pattern(regexp = ID) String operationId,
        @NotNull @Pattern(regexp = ID) String grantId,
        @NotNull @Pattern(regexp = ID) String customerId,
        @NotNull @Pattern(regexp = ID) String tenantId,
        @NotNull @Pattern(regexp = ID) String deploymentId,
        @NotNull @Pattern(regexp = ID) String targetProfileId,
        @NotNull @Pattern(regexp = ID) String releaseId,
        @NotNull @Pattern(regexp = SHA256) String scopeHash,
        @NotNull Goal goal,
        @NotNull Mode mode,
        @NotNull @Pattern(regexp = ID) String policyRevision,
        @NotNull Instant dueAt,
        @NotNull Instant deadlineAt,
        @NotNull Instant requestedAt
    ) { }

    public record Command(
        @NotNull @Pattern(regexp = "1") String schemaVersion,
        @NotNull @Pattern(regexp = ID) String commandId,
        @NotNull @Pattern(regexp = ID) String operationId,
        @NotNull @Pattern(regexp = ID) String grantId,
        @NotNull @Pattern(regexp = SHA256) String scopeHash,
        @NotNull Phase phase
    ) { }

    public record Receipt(
        @NotNull @Pattern(regexp = "1") String schemaVersion,
        @NotNull @Pattern(regexp = ID) String commandId,
        @NotNull @Pattern(regexp = ID) String operationId,
        @NotNull @Pattern(regexp = ID) String grantId,
        @NotNull Phase phase,
        @NotNull Status status,
        @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}") String code,
        @NotNull Instant observedAt,
        @NotNull @Size(max = 32) List<@NotBlank @Size(max = 256) String> blockers
    ) {
        public Receipt {
            if (blockers != null) blockers = List.copyOf(blockers);
        }
    }
}
