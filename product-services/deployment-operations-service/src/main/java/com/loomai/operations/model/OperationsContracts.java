package com.loomai.operations.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.function.BinaryOperator;

public final class OperationsContracts {

    public static final String ID = "[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}";
    public static final String SHA256 = "[a-fA-F0-9]{64}";
    private static final long MAX_DEMAND = 1_000_000_000_000L;

    private OperationsContracts() {}

    public enum Mode { OBSERVE, ENFORCE }
    public enum Decision { ALLOW, DENY, UNAVAILABLE }
    public enum OutcomeStatus { ACTIVE, FAILED, STOPPED }
    public enum AllocationState { HOLD, ACTIVE, UNCONFIRMED, STOPPED }

    public record AssessmentRequest(
        @NotNull @Pattern(regexp = "1") String schemaVersion,
        @NotNull @Pattern(regexp = ID) String commandId,
        @NotNull @Pattern(regexp = ID) String customerId,
        @NotNull @Pattern(regexp = ID) String tenantId,
        @NotNull @Pattern(regexp = ID) String deploymentId,
        @NotNull @Pattern(regexp = ID) String targetProfileId,
        @NotNull @Pattern(regexp = ID) String releaseId,
        @NotNull @Pattern(regexp = ID) String versionId,
        @NotNull @Pattern(regexp = "(?:sha256:)?[a-fA-F0-9]{64}") String versionConfigHash,
        @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String operation,
        @NotNull Mode mode,
        @NotNull @Pattern(regexp = SHA256) String requestFingerprint,
        @NotNull @Valid ResourceDemand resourceDemand,
        @NotNull Instant requestedAt
    ) {}

    public record AssessmentResponse(
        String schemaVersion,
        String commandId,
        String requestFingerprint,
        String assessmentId,
        Decision decision,
        String reason,
        String policyRevision,
        Instant observedAt,
        Instant expiresAt
    ) {}

    public record OutcomeRequest(
        @NotNull @Pattern(regexp = "1") String schemaVersion,
        @NotNull @Pattern(regexp = ID) String commandId,
        @NotNull @Pattern(regexp = SHA256) String requestFingerprint,
        @NotNull OutcomeStatus status,
        @NotNull Instant observedAt
    ) {}

    public record ResourceDemand(
        @NotNull @Min(0) @Max(MAX_DEMAND) Long cpuMillis,
        @NotNull @Min(0) @Max(MAX_DEMAND) Long memoryMiB,
        @NotNull @Min(0) @Max(MAX_DEMAND) Long diskMiB,
        @NotNull @Min(0) @Max(MAX_DEMAND) Long databases,
        @NotNull @Min(0) @Max(MAX_DEMAND) Long indexes,
        @NotNull @Min(0) @Max(MAX_DEMAND) Long dependencies,
        @NotNull @Min(0) @Max(MAX_DEMAND) Long replicas,
        @NotNull @Min(0) @Max(MAX_DEMAND) Long concurrentApplies
    ) {
        public static ResourceDemand zero() {
            return new ResourceDemand(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        public boolean validRequest() {
            return cpuMillis > 0 && memoryMiB > 0 && diskMiB >= 0 && replicas > 0 && concurrentApplies > 0;
        }

        public ResourceDemand max(ResourceDemand other) {
            return combine(other, Math::max);
        }

        public ResourceDemand plus(ResourceDemand other) {
            return combine(other, (left, right) -> left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right);
        }

        public ResourceDemand withoutApplySlot() {
            return new ResourceDemand(cpuMillis, memoryMiB, diskMiB, databases, indexes, dependencies, replicas, 0L);
        }

        public ResourceDemand retainedAfterStop() {
            return new ResourceDemand(0L, 0L, diskMiB, databases, indexes, dependencies, 0L, 0L);
        }

        public boolean fits(ResourceDemand capacity) {
            return cpuMillis <= capacity.cpuMillis && memoryMiB <= capacity.memoryMiB
                && diskMiB <= capacity.diskMiB && databases <= capacity.databases
                && indexes <= capacity.indexes && dependencies <= capacity.dependencies
                && replicas <= capacity.replicas && concurrentApplies <= capacity.concurrentApplies;
        }

        private ResourceDemand combine(ResourceDemand other, BinaryOperator<Long> operator) {
            return new ResourceDemand(
                operator.apply(cpuMillis, other.cpuMillis), operator.apply(memoryMiB, other.memoryMiB),
                operator.apply(diskMiB, other.diskMiB), operator.apply(databases, other.databases),
                operator.apply(indexes, other.indexes), operator.apply(dependencies, other.dependencies),
                operator.apply(replicas, other.replicas), operator.apply(concurrentApplies, other.concurrentApplies)
            );
        }
    }

    public record Allocation(
        String customerId,
        String tenantId,
        String deploymentId,
        String targetProfileId,
        String commandId,
        ResourceDemand demand,
        AllocationState state,
        Instant holdExpiresAt,
        Instant updatedAt
    ) {}

    public record StoredAssessment(
        AssessmentRequest request,
        String requestDigest,
        AssessmentResponse response,
        OutcomeRequest lastOutcome
    ) {}
}
