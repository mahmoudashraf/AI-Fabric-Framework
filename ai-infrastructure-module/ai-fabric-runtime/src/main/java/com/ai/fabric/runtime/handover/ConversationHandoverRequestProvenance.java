package com.ai.fabric.runtime.handover;

/**
 * Structured model output used only to resolve duplicate-confirmation policy.
 */
public enum ConversationHandoverRequestProvenance {
    EXPLICIT_USER_REQUEST,
    MODEL_RECOMMENDATION
}
