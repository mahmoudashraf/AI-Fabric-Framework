package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.openai.OpenAiBudgetScopeResolver;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import java.util.Objects;

/** Deployment-scoped native transport binding, including background embeddings; no request/thread authority. */
public final class RuntimeOpenAiBudgetScopeResolver implements OpenAiBudgetScopeResolver {
    private final RuntimeBudgetBindingService budgets;

    public RuntimeOpenAiBudgetScopeResolver(RuntimeBudgetBindingService budgets) {
        this.budgets = Objects.requireNonNull(budgets);
    }

    @Override
    public Binding requireBinding(String credentialIdentity) {
        var expected = budgets.approval();
        if (!expected.scope().credentialIdentity().equals(credentialIdentity)) {
            throw new OpenAiBudgetException(OpenAiBudgetException.Code.CREDENTIAL_SCOPE_MISMATCH);
        }
        if (!ReviewedOpenAiRateCard.isReviewedReference(expected.scope().rateCardReference())) {
            throw new OpenAiBudgetException(OpenAiBudgetException.Code.RATE_CARD_MISMATCH);
        }
        return new Binding(expected.id(), expected.scope());
    }
}
