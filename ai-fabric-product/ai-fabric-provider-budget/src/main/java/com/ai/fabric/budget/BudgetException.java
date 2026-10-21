package com.ai.fabric.budget;

/** Explicit failure without request bodies, credentials, SQL details or provider data in the message. */
public final class BudgetException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST, NOT_FOUND, SCOPE_MISMATCH, APPROVAL_CONFLICT, ATTEMPT_CONFLICT,
        SETTLEMENT_CONFLICT, LIMIT_EXCEEDED, STOPPED, EXPIRED, ARITHMETIC_OVERFLOW, STORAGE_UNAVAILABLE,
        VALIDITY_EXTENSION_CONFLICT, VALIDITY_EXTENSION_EXPIRY_CHANGED
    }

    private final Code code;
    private final String reason;

    public BudgetException(Code code) { this(code, code.name()); }

    BudgetException(Code code, String reason) { super(code.name()); this.code = code; this.reason = reason; }

    public Code code() { return code; }

    /** Fixed machine-readable reason; no provider payload, SQL text or customer input. */
    public String reason() { return reason; }
}
