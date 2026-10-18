package com.ai.fabric.runtime.budget;

/** Fixed machine-readable failures only; no SQL, credentials or untrusted error messages. */
public final class RuntimeBudgetBindingException extends RuntimeException {
    public enum Code {
        DISABLED, NOT_INITIALIZED, NOT_ACTIVE, BINDING_CONFLICT, ALLOCATION_SCOPE_MISMATCH,
        ALLOCATION_FINGERPRINT_MISMATCH, STORAGE_UNAVAILABLE
    }
    private final Code code;
    public RuntimeBudgetBindingException(Code code) { super("RUNTIME_BUDGET_" + code.name()); this.code = code; }
    public Code code() { return code; }
}
