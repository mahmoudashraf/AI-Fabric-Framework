package com.ai.infrastructure.intent.orchestration;

public enum OrchestrationResultType {
    ACTION_EXECUTED,
    ACTION_DENIED,
    INFORMATION_PROVIDED,
    CLARIFICATION_REQUIRED,
    OUT_OF_SCOPE,
    COMPOUND_HANDLED,
    ERROR
}
