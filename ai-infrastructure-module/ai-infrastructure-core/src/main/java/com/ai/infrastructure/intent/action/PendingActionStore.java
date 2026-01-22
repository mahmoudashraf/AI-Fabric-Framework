package com.ai.infrastructure.intent.action;

import java.util.Optional;

/**
 * Storage abstraction for pending (confirmation-required) actions.
 *
 * <p>Implementations may persist per-conversation pending actions in a database, Redis, etc.</p>
 */
public interface PendingActionStore {

    Optional<PendingAction> getPendingAction(String conversationId, String ownerId);

    void savePendingAction(String conversationId, String ownerId, PendingAction pendingAction);

    void clearPendingAction(String conversationId, String ownerId);

    /**
     * Peek current pending action (stack-compatible).
     */
    default Optional<PendingAction> peekPendingAction(String conversationId, String ownerId) {
        return getPendingAction(conversationId, ownerId);
    }

    /**
     * Push a pending action (stack-compatible).
     */
    default void pushPendingAction(String conversationId, String ownerId, PendingAction pendingAction) {
        savePendingAction(conversationId, ownerId, pendingAction);
    }

    /**
     * Pop current pending action and return it (stack-compatible).
     */
    default Optional<PendingAction> popPendingAction(String conversationId, String ownerId) {
        Optional<PendingAction> current = getPendingAction(conversationId, ownerId);
        clearPendingAction(conversationId, ownerId);
        return current;
    }

    /**
     * Clear all pending actions (stack-compatible).
     */
    default void clearPendingActions(String conversationId, String ownerId) {
        clearPendingAction(conversationId, ownerId);
    }
}
