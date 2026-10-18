package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.ProviderBudgetLedger;

/** Trusted server integration, never a customer-supplied approval or scope. */
@FunctionalInterface
public interface OpenAiBudgetScopeResolver {
    /** Resolve for the SHA-256 identity of the actual outgoing bearer credential. */
    Binding requireBinding(String credentialIdentity);

    record Binding(String approvalId, ProviderBudgetLedger.Scope scope) {
        public Binding {
            if (approvalId == null || approvalId.isBlank() || scope == null) {
                throw new OpenAiBudgetException(OpenAiBudgetException.Code.CREDENTIAL_SCOPE_MISMATCH);
            }
        }
    }
}
