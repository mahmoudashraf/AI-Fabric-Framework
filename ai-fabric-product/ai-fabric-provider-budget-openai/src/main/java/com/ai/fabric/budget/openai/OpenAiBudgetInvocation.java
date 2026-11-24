package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.BudgetException;

import java.util.Objects;
import java.util.UUID;

/** Correlation only. It neither carries nor selects a billing approval, scope or credential. */
public record OpenAiBudgetInvocation(UUID operationId, DenialObserver observer, CompletionObserver completionObserver) {
    public static final OpenAiBudgetInvocation NONE = new OpenAiBudgetInvocation(null, failure -> { });

    public OpenAiBudgetInvocation(UUID operationId, DenialObserver observer) {
        this(operationId, observer, completion -> { });
    }

    public OpenAiBudgetInvocation {
        Objects.requireNonNull(observer);
        Objects.requireNonNull(completionObserver);
    }

    public enum Operation { CHAT_COMPLETION, EMBEDDING, UNSUPPORTED }

    @FunctionalInterface public interface Capture {
        /** Called synchronously during model resolution, before any native SDK async dispatch. */
        OpenAiBudgetInvocation capture(Operation operation);
    }

    @FunctionalInterface public interface DenialObserver { void onDenied(Denial failure); }

    @FunctionalInterface public interface CompletionObserver {
        /** May run on the native SDK worker. Called only after native usage settles successfully. */
        void onCompleted(Completion completion);
    }

    public record Denial(UUID operationId, UUID attemptId, BudgetException.Code budgetCode,
                         OpenAiBudgetException.Code guardCode) { }

    /**
     * Native exchange evidence, not a claim that SDK parsing or generation succeeded. No bodies,
     * headers, credentials, parameters or reasoning text. Optional/unrecognized metadata is null.
     * Content length is Java string length; empty includes blank text and explicit null content.
     * Chat-only fields are null for embeddings. Reasoning tokens are a subset of output tokens.
     */
    public record Completion(UUID operationId, UUID attemptId, String providerRequestId, Operation operation,
                             String model, String responseModel, Long effectiveOutputCap, String finishReason,
                             long inputTokens, Long outputTokens, Long reasoningTokens,
                             Integer contentLength, Boolean contentEmpty) { }

    void completed(Completion completion) {
        try {
            completionObserver.onCompleted(completion);
        } catch (RuntimeException unavailable) {
            // Optional diagnostics cannot turn a settled exchange into an ambiguous outcome or retry.
        }
    }

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
