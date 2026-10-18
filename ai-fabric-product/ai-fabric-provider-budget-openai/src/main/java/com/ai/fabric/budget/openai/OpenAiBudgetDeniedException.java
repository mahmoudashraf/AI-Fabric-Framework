package com.ai.fabric.budget.openai;

/** Not an SDK retryable/IO exception: denial of this attempt must reach the host immediately. */
public final class OpenAiBudgetDeniedException extends RuntimeException {
    OpenAiBudgetDeniedException(OpenAiBudgetTransportException failure) { super("OPENAI_ATTEMPT_DENIED", failure); }
    public OpenAiBudgetTransportException failure() { return (OpenAiBudgetTransportException) getCause(); }
}
