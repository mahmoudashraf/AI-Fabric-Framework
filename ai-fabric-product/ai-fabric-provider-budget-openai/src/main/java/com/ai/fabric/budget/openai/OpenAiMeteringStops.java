package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared across resolver roots so a failed stop write cannot reopen another transport for the same approval. */
final class OpenAiMeteringStops {
    private final ProviderBudgetLedger ledger;
    private final Set<OpenAiBudgetScopeResolver.Binding> breached = ConcurrentHashMap.newKeySet();

    OpenAiMeteringStops(ProviderBudgetLedger ledger) { this.ledger = java.util.Objects.requireNonNull(ledger); }

    boolean contains(OpenAiBudgetScopeResolver.Binding binding) { return breached.contains(binding); }

    void requireUnbreached(OpenAiBudgetScopeResolver.Binding binding) {
        if (!contains(binding)) return;
        stop(binding);
        throw new BudgetException(BudgetException.Code.STOPPED);
    }

    void stop(OpenAiBudgetScopeResolver.Binding binding) {
        breached.add(binding);
        ProviderBudgetLedger.Snapshot snapshot;
        try { snapshot = ledger.stop(binding.approvalId(), binding.scope(), "METERING_BOUND_BREACH"); }
        catch (BudgetException failure) {
            if (failure.code() != BudgetException.Code.ATTEMPT_CONFLICT) throw failure;
            // An independently stopped approval is already closed, even with a different stop reason.
            snapshot = ledger.read(binding.approvalId(), binding.scope());
        }
        if (snapshot == null || !snapshot.stopped()) throw new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE);
    }
}
