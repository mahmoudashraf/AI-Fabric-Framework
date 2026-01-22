package com.ai.infrastructure.chat.util;

import com.ai.infrastructure.intent.action.PendingAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility for storing a stack of pending confirmation actions inside chat session metadata.
 *
 * <p>State is stored under the {@code confirmationStack} key as a list of maps.</p>
 */
public final class ConfirmationStack {

    public static final String METADATA_KEY_STACK = "confirmationStack";

    private ConfirmationStack() {
    }

    public static PendingAction peek(Map<String, Object> metadata) {
        List<Map<String, Object>> stack = getStack(metadata);
        if (stack.isEmpty()) {
            return null;
        }
        Map<String, Object> top = stack.get(stack.size() - 1);
        return PendingAction.fromMap(top);
    }

    public static PendingAction pop(Map<String, Object> metadata) {
        List<Map<String, Object>> stack = getStack(metadata);
        if (stack.isEmpty()) {
            return null;
        }
        Map<String, Object> top = stack.remove(stack.size() - 1);
        metadata.put(METADATA_KEY_STACK, stack);
        return PendingAction.fromMap(top);
    }

    public static void push(Map<String, Object> metadata, PendingAction pendingAction) {
        if (metadata == null || pendingAction == null) {
            return;
        }
        List<Map<String, Object>> stack = getStack(metadata);
        stack.add(pendingAction.toMap());
        metadata.put(METADATA_KEY_STACK, stack);
    }

    public static void clear(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        metadata.remove(METADATA_KEY_STACK);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getStack(Map<String, Object> metadata) {
        if (metadata == null) {
            return new ArrayList<>();
        }
        Object raw = metadata.get(METADATA_KEY_STACK);
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }
}

