package com.ai.infrastructure.intent.action;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.util.StringUtils;

/**
 * Default (non-durable) pending action store.
 */
public class InMemoryPendingActionStore implements PendingActionStore {

    private final ConcurrentMap<String, PendingAction> store = new ConcurrentHashMap<>();

    @Override
    public Optional<PendingAction> getPendingAction(String conversationId, String ownerId) {
        String key = key(conversationId, ownerId);
        return key == null ? Optional.empty() : Optional.ofNullable(store.get(key));
    }

    @Override
    public void savePendingAction(String conversationId, String ownerId, PendingAction pendingAction) {
        String key = key(conversationId, ownerId);
        if (key == null || pendingAction == null) {
            return;
        }
        store.put(key, pendingAction);
    }

    @Override
    public void clearPendingAction(String conversationId, String ownerId) {
        String key = key(conversationId, ownerId);
        if (key == null) {
            return;
        }
        store.remove(key);
    }

    private String key(String conversationId, String ownerId) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(ownerId)) {
            return null;
        }
        return conversationId.trim() + "|" + ownerId.trim();
    }
}

