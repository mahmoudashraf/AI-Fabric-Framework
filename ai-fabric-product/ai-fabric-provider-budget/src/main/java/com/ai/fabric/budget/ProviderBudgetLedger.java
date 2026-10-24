package com.ai.fabric.budget;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

/** Trusted server API. A receipt establishes accounting, not transport or billing correctness. */
public interface ProviderBudgetLedger {
    Snapshot approve(Approval approval);

    /** Allocate a fresh server UUID for each ingress, including each HTTP retry. */
    ChatReceipt recordChatIngress(String approvalId, Scope scope, UUID serverAttemptId);

    /** Only newlyReserved=true may correspond to a new send. A replay never authorizes retransmission. */
    ReservationReceipt reserveAttempt(String approvalId, Scope scope, UUID attemptId, Usage upperBound);

    /** Token counts are measured; cost may be a conservative accounted upper bound, not an invoice. */
    Attempt settle(String approvalId, Scope scope, UUID attemptId, Usage settledUsage, String evidenceReference);

    Attempt markAmbiguous(String approvalId, Scope scope, UUID attemptId, String reason);

    Snapshot stop(String approvalId, Scope scope, String reason);

    Snapshot read(String approvalId, Scope scope);

    /** Separately authorized additive credit; never resets usage, expiry, holds or stop state. */
    default TopUpReceipt topUp(Approval original, TopUpCommand command) {
        throw new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE);
    }

    default Optional<TopUpReceipt> readTopUp(String approvalId, Scope scope, String topUpId) {
        throw new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE);
    }

    record TopUpCommand(int schemaVersion, String topUpId, String approvalId, UUID storeUuid,
                        String runtimeFingerprint, Limits expectedEffectiveLimits, Limits additionalLimits,
                        String authorizedBy, String approvalEvidenceRef, String payerRef, String reason) {
        public TopUpCommand {
            if (schemaVersion != 1 || storeUuid == null || expectedEffectiveLimits == null || additionalLimits == null
                || runtimeFingerprint == null || !runtimeFingerprint.matches("[a-f0-9]{64}")) throw invalid();
            topUpId = text(topUpId, 128); approvalId = text(approvalId, 128);
            if (!topUpId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw invalid();
            authorizedBy = text(authorizedBy, 256); approvalEvidenceRef = text(approvalEvidenceRef, 255);
            payerRef = text(payerRef, 255); reason = text(reason, 512);
            if (additionalLimits.equals(new Limits(0, 0, 0))) throw invalid();
            addLimits(expectedEffectiveLimits, additionalLimits);
        }
        @Override public String toString() { return "TopUpCommand[topUpId=" + topUpId + "]"; }
    }

    record TopUpReceipt(int schemaVersion, String topUpId, String approvalId, Scope scope, UUID storeUuid,
                        String runtimeFingerprint, Limits previousEffectiveLimits, Limits additionalLimits,
                        Limits effectiveLimits, String authorizedBy, String approvalEvidenceRef, String payerRef,
                        String reasonSha256, Instant authorizedAt) { }

    /** Separate from the immutable original approval; unsupported adapters remain conservative. */
    default Instant effectiveExpiresAt(String approvalId, Scope scope) { return read(approvalId, scope).approval().expiresAt(); }

    /** Trusted host must fence its ACTIVE store binding before calling this accounting-only mutation. */
    default ValidityExtensionReceipt extendValidity(Approval original, ValidityExtensionCommand command) {
        throw new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE);
    }

    default Optional<ValidityExtensionReceipt> readValidityExtension(String approvalId, Scope scope, String extensionId) {
        throw new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE);
    }

    record ValidityExtensionCommand(int schemaVersion, String extensionId, String approvalId, UUID storeUuid,
                                    String runtimeFingerprint, Instant expectedEffectiveExpiresAt, Instant expiresAt, String reason) {
        public ValidityExtensionCommand {
            if (schemaVersion != 1 || storeUuid == null || expectedEffectiveExpiresAt == null || expiresAt == null
                || runtimeFingerprint == null || !runtimeFingerprint.matches("[a-f0-9]{64}")) throw invalid();
            extensionId = text(extensionId, 128); approvalId = text(approvalId, 128); reason = text(reason, 512);
        }
        @Override public String toString() {
            return "ValidityExtensionCommand[extensionId=" + extensionId + ", approvalId=" + approvalId + ", reason=REDACTED]";
        }
    }

    record ValidityExtensionReceipt(int schemaVersion, String extensionId, String approvalId, Scope scope,
                                    UUID storeUuid, String runtimeFingerprint, Instant previousEffectiveExpiresAt,
                                    Instant expiresAt, String reasonSha256, Instant authorizedAt) { }

    record Scope(String customerId, String deploymentId, String targetProfileId, String environment,
                 String credentialIdentity, String rateCardReference) {
        public Scope {
            customerId = text(customerId, 256); deploymentId = text(deploymentId, 256);
            targetProfileId = text(targetProfileId, 256); environment = text(environment, 256);
            credentialIdentity = text(credentialIdentity, 256);
            rateCardReference = text(rateCardReference, 256);
        }
    }

    record Limits(long chatAttempts, long totalTokens, long costNanoUsd) {
        public Limits { nonnegative(chatAttempts); nonnegative(totalTokens); nonnegative(costNanoUsd); }
    }

    record Approval(String id, Scope scope, Limits limits, Instant expiresAt) {
        public Approval {
            id = text(id, 128);
            if (scope == null || limits == null || expiresAt == null) throw invalid();
        }
    }

    /** costNanoUsd is conservative accounted cost; this API never claims invoice-exact billing. */
    record Usage(long inputTokens, long outputTokens, long costNanoUsd) {
        public Usage {
            nonnegative(inputTokens); nonnegative(outputTokens); nonnegative(costNanoUsd);
            checkedAdd(inputTokens, outputTokens);
        }
        public long totalTokens() { return checkedAdd(inputTokens, outputTokens); }
    }

    enum AttemptState { RESERVED, AMBIGUOUS, SETTLED }

    record Attempt(UUID id, Usage upperBound, AttemptState state, Usage settledUsage,
                   String evidenceReference, String ambiguityReason) { }

    record ReservationReceipt(Attempt attempt, boolean newlyReserved) { }

    record ChatReceipt(UUID serverAttemptId, long ordinal, boolean newlyCounted) { }

    /** accountedUsage = settled usage + full holds for RESERVED/AMBIGUOUS attempts. */
    record Snapshot(Approval approval, long chatAttempts, long providerAttempts,
                    Usage accountedUsage, boolean stopped, String stopReason, Limits effectiveLimits) {
        public Snapshot(Approval approval, long chatAttempts, long providerAttempts, Usage accountedUsage,
                        boolean stopped, String stopReason) {
            this(approval, chatAttempts, providerAttempts, accountedUsage, stopped, stopReason, approval.limits());
        }
    }

    static Limits addLimits(Limits left, Limits right) {
        return new Limits(checkedAdd(left.chatAttempts(), right.chatAttempts()),
            checkedAdd(left.totalTokens(), right.totalTokens()), checkedAdd(left.costNanoUsd(), right.costNanoUsd()));
    }

    private static void nonnegative(long value) { if (value < 0) throw invalid(); }

    static String text(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) throw invalid();
        return value;
    }

    static long checkedAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException error) { throw new BudgetException(BudgetException.Code.ARITHMETIC_OVERFLOW); }
    }

    private static BudgetException invalid() { return new BudgetException(BudgetException.Code.INVALID_REQUEST); }
}
