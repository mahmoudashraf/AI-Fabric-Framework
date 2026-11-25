package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.ProviderBudgetLedger;

/** Trusted server integration, never a customer-supplied approval or scope. */
@FunctionalInterface
public interface OpenAiBudgetScopeResolver {
    /** Resolve for the SHA-256 identity of the actual outgoing bearer credential. */
    Binding requireBinding(String credentialIdentity);

    default Binding requireBinding(String credentialIdentity, String model) { return requireBinding(credentialIdentity); }

    record Binding(String approvalId, ProviderBudgetLedger.Scope scope, String additionalRateCardReference, String pricingAmendmentId) {
        public Binding(String approvalId, ProviderBudgetLedger.Scope scope) { this(approvalId, scope, null, null); }
        public Binding {
            if (approvalId == null || approvalId.isBlank() || scope == null) {
                throw new OpenAiBudgetException(OpenAiBudgetException.Code.CREDENTIAL_SCOPE_MISMATCH);
            }
            if (additionalRateCardReference != null || pricingAmendmentId != null) {
                if (!ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE.equals(scope.rateCardReference())
                    || !ReviewedOpenAiRateCard.ASTRA_REFERENCE.equals(additionalRateCardReference)
                    || pricingAmendmentId == null || !pricingAmendmentId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}"))
                    throw new OpenAiBudgetException(OpenAiBudgetException.Code.RATE_CARD_MISMATCH);
            }
        }
    }
}
