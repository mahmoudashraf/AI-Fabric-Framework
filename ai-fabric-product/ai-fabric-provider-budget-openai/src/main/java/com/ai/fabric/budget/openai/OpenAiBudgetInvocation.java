package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.BudgetException;

import java.util.Objects;
import java.util.UUID;

/** Correlation only. It neither carries nor selects a billing approval, scope or credential. */
public record OpenAiBudgetInvocation(UUID operationId, DenialObserver observer) {
    public static final OpenAiBudgetInvocation NONE = new OpenAiBudgetInvocation(null, failure -> { });

    public OpenAiBudgetInvocation { Objects.requireNonNull(observer); }

    public enum Operation { CHAT_COMPLETION, EMBEDDING, UNSUPPORTED }

    @FunctionalInterface public interface Capture {
        /** Called synchronously during model resolution, before any native SDK async dispatch. */
        OpenAiBudgetInvocation capture(Operation operation);
    }

    @FunctionalInterface public interface DenialObserver { void onDenied(Denial failure); }

    public record Denial(UUID operationId, UUID attemptId, BudgetException.Code budgetCode,
                         OpenAiBudgetException.Code guardCode) { }

    void denied(OpenAiBudgetTransportException failure) {
        try {
            observer.onDenied(new Denial(operationId, failure.attemptId(), failure.budgetCode().orElse(null),
                failure.guardCode().orElse(null)));
        } catch (RuntimeException unavailable) {
            // Preserve the typed denial even if a host observer fails; never copy host prose into evidence.
            failure.addSuppressed(new IllegalStateException("DENIAL_OBSERVER_FAILED"));
        }
    }
}
