package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver.OutputCapMode;
import com.ai.fabric.budget.openai.GuardedReasoningEffort;

/** Trusted startup binding only. Approval IDs, financial caps and expiry are intentionally absent. */
public record RuntimeProviderBudgetProperties(String environment, String credentialIdentity, String rateCardReference,
                                              OutputCapMode outputCapMode, GuardedReasoningEffort reasoningEffort) {
    public RuntimeProviderBudgetProperties(String environment, String credentialIdentity, String rateCardReference,
        OutputCapMode outputCapMode) {
        this(environment, credentialIdentity, rateCardReference, outputCapMode, GuardedReasoningEffort.NONE);
    }
    public RuntimeProviderBudgetProperties {
        java.util.Objects.requireNonNull(reasoningEffort);
        if (outputCapMode != OutputCapMode.MAX_COMPLETION_TOKENS) {
            throw new IllegalArgumentException("RUNTIME_BUDGET_EXPLICIT_MAX_COMPLETION_TOKENS_REQUIRED");
        }
    }
}
