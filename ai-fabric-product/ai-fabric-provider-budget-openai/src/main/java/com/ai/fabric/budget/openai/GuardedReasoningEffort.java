package com.ai.fabric.budget.openai;

/** Trusted host selection; all reasoning remains inside the existing completion-token cap. */
public enum GuardedReasoningEffort {
    NONE("none"), LOW("low"), MEDIUM("medium");

    private final String wireValue;
    GuardedReasoningEffort(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
