package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver.OutputCapMode;

/** Trusted startup binding only. Approval IDs, financial caps and expiry are intentionally absent. */
public record RuntimeProviderBudgetProperties(String environment, String credentialIdentity, String rateCardReference,
                                              OutputCapMode outputCapMode) {
    public RuntimeProviderBudgetProperties {
        if (outputCapMode != OutputCapMode.MAX_COMPLETION_TOKENS) {
            throw new IllegalArgumentException("RUNTIME_BUDGET_EXPLICIT_MAX_COMPLETION_TOKENS_REQUIRED");
        }
    }
}
