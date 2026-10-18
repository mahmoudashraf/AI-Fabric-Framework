package com.ai.fabric.budget.openai;

/** Messages deliberately exclude credentials, prompts, provider bodies and URLs. */
public final class OpenAiBudgetException extends RuntimeException {
    public enum Code { UNSUPPORTED_PROVIDER, UNSUPPORTED_CONFIGURATION, UNSUPPORTED_REQUEST,
        CREDENTIAL_SCOPE_MISMATCH, RATE_CARD_MISMATCH, ATTEMPT_REPLAY, ATTEMPT_AMBIGUOUS, METERING_BOUND_BREACH,
        UNSUPPORTED_OUTPUT_LIMIT_MODE, INVOCATION_CONTEXT_REQUIRED }

    private final Code code;

    public OpenAiBudgetException(Code code) { super(code.name()); this.code = code; }

    public Code code() { return code; }
}
