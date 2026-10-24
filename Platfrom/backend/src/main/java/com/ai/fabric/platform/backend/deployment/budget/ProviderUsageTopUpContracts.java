package com.ai.fabric.platform.backend.deployment.budget;

import java.time.Instant;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

public final class ProviderUsageTopUpContracts {
    private ProviderUsageTopUpContracts() { }

    /** Per-command staff funding bound: 10,000 interactive attempts, 100M tokens, USD 100. No validity change. */
    public static final Limits MAX_ADDITION = new Limits(10_000, 100_000_000, 100_000_000_000L);

    public record ReviewRequest(String topUpId, AllocationScope scope, String releaseId,
                                String expectedApprovalFingerprint, UUID storeUuid,
                                Limits expectedEffectiveLimits, Limits additionalLimits,
                                String approvalEvidenceRef, String payerRef, String reason) {
        public ReviewRequest {
            topUpId = identifier(topUpId); releaseId = text(releaseId, 64);
            expectedApprovalFingerprint = sha(expectedApprovalFingerprint);
            approvalEvidenceRef = text(approvalEvidenceRef, 255); payerRef = text(payerRef, 255); reason = text(reason, 512);
            if (scope == null || storeUuid == null || expectedEffectiveLimits == null) throw invalid();
            bounded(additionalLimits); add(expectedEffectiveLimits, additionalLimits);
        }
    }

    /** Exact runtime wire contract. authorizedBy is supplied by the staff review, never the request. */
    public record RuntimeCommand(int schemaVersion, String topUpId, String approvalId, UUID storeUuid,
                                 String runtimeFingerprint, Limits expectedEffectiveLimits, Limits additionalLimits,
                                 String authorizedBy, String approvalEvidenceRef, String payerRef, String reason) {
        public RuntimeCommand {
            if (schemaVersion != 1 || storeUuid == null || expectedEffectiveLimits == null) throw invalid();
            topUpId = identifier(topUpId); approvalId = text(approvalId, 96); runtimeFingerprint = sha(runtimeFingerprint);
            authorizedBy = text(authorizedBy, 255); approvalEvidenceRef = text(approvalEvidenceRef, 255);
            payerRef = text(payerRef, 255); reason = text(reason, 512);
            bounded(additionalLimits); add(expectedEffectiveLimits, additionalLimits);
        }
        @Override public String toString() { return "RuntimeCommand[topUpId=" + topUpId + "]"; }
    }

    public record RuntimeReceipt(int schemaVersion, String topUpId, String approvalId,
                                 ProviderUsageValidityExtensionContracts.RuntimeScope scope, UUID storeUuid,
                                 String runtimeFingerprint, Limits previousEffectiveLimits, Limits additionalLimits,
                                 Limits effectiveLimits, String authorizedBy, String approvalEvidenceRef, String payerRef,
                                 String reasonSha256, Instant authorizedAt) { }

    public record Command(ReviewRequest review, RuntimeCommand runtimeCommand, String runtimeBaseUrl,
                          String authorityFingerprint, String capabilityManifestHash, String reviewFingerprint) {
        public Command {
            if (review == null || runtimeCommand == null || !review.topUpId().equals(runtimeCommand.topUpId())
                || !review.scope().approvalId().equals(runtimeCommand.approvalId()) || !review.storeUuid().equals(runtimeCommand.storeUuid())
                || !review.expectedEffectiveLimits().equals(runtimeCommand.expectedEffectiveLimits())
                || !review.additionalLimits().equals(runtimeCommand.additionalLimits())
                || !review.approvalEvidenceRef().equals(runtimeCommand.approvalEvidenceRef())
                || !review.payerRef().equals(runtimeCommand.payerRef()) || !review.reason().equals(runtimeCommand.reason())) throw invalid();
            runtimeBaseUrl = text(runtimeBaseUrl, 2048); authorityFingerprint = sha(authorityFingerprint);
            capabilityManifestHash = sha(capabilityManifestHash); reviewFingerprint = sha(reviewFingerprint);
        }
    }

    public enum Status { REVIEWED, UNKNOWN, ACKNOWLEDGED }
    public record Plan(Command command, Limits originalLimits, Instant originalExpiresAt, boolean financialMutationPerformed) { }
    public record Result(String topUpId, Status status, Limits originalLimits, Limits effectiveLimits,
                         Instant originalExpiresAt, Instant effectiveExpiresAt, RuntimeReceipt receipt) { }
    record Review(Command command, String actorId, Instant reviewedAt) { }
    record Event(String topUpId, String eventType, String commandFingerprint, String actorId, Instant recordedAt, RuntimeReceipt receipt) { }

    static String identifier(String value) {
        text(value, 128);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw invalid();
        return value;
    }

    static Limits add(Limits left, Limits right) {
        try {
            return new Limits(Math.addExact(left.chatAttempts(), right.chatAttempts()),
                Math.addExact(left.totalTokens(), right.totalTokens()), Math.addExact(left.costNanoUsd(), right.costNanoUsd()));
        } catch (ArithmeticException overflow) { throw conflict("PROVIDER_USAGE_TOP_UP_LIMITS_OVERFLOW"); }
    }

    private static void bounded(Limits value) {
        if (value == null || value.equals(new Limits(0, 0, 0)) || value.chatAttempts() > MAX_ADDITION.chatAttempts()
            || value.totalTokens() > MAX_ADDITION.totalTokens() || value.costNanoUsd() > MAX_ADDITION.costNanoUsd()) throw invalid();
    }
}
