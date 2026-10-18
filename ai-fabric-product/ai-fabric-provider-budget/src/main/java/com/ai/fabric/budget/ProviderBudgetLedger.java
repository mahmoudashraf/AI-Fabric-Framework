package com.ai.fabric.budget;

import java.time.Instant;
import java.util.UUID;

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
                    Usage accountedUsage, boolean stopped, String stopReason) { }

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
