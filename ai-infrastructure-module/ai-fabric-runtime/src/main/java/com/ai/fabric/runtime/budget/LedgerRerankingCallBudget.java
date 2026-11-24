package com.ai.fabric.runtime.budget;

import ai.fabric.spi.RerankingCallBudget;
import ai.fabric.spi.RerankingProvider;
import com.ai.fabric.budget.ProviderBudgetLedger;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Reuses the durable ledger with a separately authorized provider/credential/rate-card allocation. */
public final class LedgerRerankingCallBudget implements RerankingCallBudget {
    private final ProviderBudgetLedger ledger;
    private final Supplier<ProviderBudgetLedger.Approval> activeApproval;
    private final Supplier<String> credential;
    private final String model;

    public LedgerRerankingCallBudget(ProviderBudgetLedger ledger, Supplier<ProviderBudgetLedger.Approval> activeApproval,
                                    Supplier<String> credential, String model) {
        this.ledger = Objects.requireNonNull(ledger);
        this.activeApproval = Objects.requireNonNull(activeApproval);
        this.credential = Objects.requireNonNull(credential);
        ReviewedCohereRerankingRateCard.nanoUsdPerToken(model);
        this.model = model;
    }

    @Override
    public Attempt reserve(String provider, String requestedModel, RerankingProvider.Request request) {
        if (!"cohere".equals(provider) || !model.equals(requestedModel)) throw new SecurityException("RERANKING_PROVIDER_SCOPE_MISMATCH");
        var approval = Objects.requireNonNull(activeApproval.get());
        var scope = approval.scope();
        if (!ReviewedCohereRerankingRateCard.REFERENCE.equals(scope.rateCardReference())
            || !ReviewedCohereRerankingRateCard.credentialIdentity(credential.get()).equals(scope.credentialIdentity())) {
            throw new SecurityException("RERANKING_CREDENTIAL_OR_RATE_SCOPE_MISMATCH");
        }
        var bound = ReviewedCohereRerankingRateCard.upperBound(model, request);
        UUID id = UUID.randomUUID();
        var reservation = ledger.reserveAttempt(approval.id(), scope, id, bound);
        if (!reservation.newlyReserved()) throw new IllegalStateException("RERANKING_RESERVATION_CANNOT_BE_REPLAYED");
        return new Attempt() {
            @Override public UUID id() { return id; }
            @Override public void settle(RerankingProvider.Usage nativeUsage, String providerRequestId) {
                var usage = ReviewedCohereRerankingRateCard.measured(model, nativeUsage);
                if (usage.totalTokens() > bound.totalTokens() || usage.costNanoUsd() > bound.costNanoUsd()) {
                    ledger.stop(approval.id(), scope, "RERANKING_NATIVE_USAGE_EXCEEDS_RESERVED_CONTRACT");
                    throw new IllegalStateException("RERANKING_USAGE_EXCEEDS_RESERVED_CONTRACT");
                }
                String evidence = providerRequestId == null || providerRequestId.isBlank()
                    ? "cohere-attempt:" + id : "cohere-native:" + providerRequestId;
                ledger.settle(approval.id(), scope, id, usage, evidence);
            }
            @Override public void ambiguous(String causeCode) {
                ledger.markAmbiguous(approval.id(), scope, id, "RERANKING_NATIVE_OUTCOME_OR_ACCOUNTING_UNVERIFIED");
            }
        };
    }
}
