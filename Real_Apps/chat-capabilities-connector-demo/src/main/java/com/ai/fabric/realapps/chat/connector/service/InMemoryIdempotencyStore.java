package com.ai.fabric.realapps.chat.connector.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryIdempotencyStore {

    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public Map<String, Object> get(String actionId, String idempotencyKey) {
        String key = key(actionId, idempotencyKey);
        if (!StringUtils.hasText(key)) {
            return null;
        }
        Map<String, Object> value = store.get(key);
        return value != null ? new LinkedHashMap<>(value) : null;
    }

    public void putIfAbsent(String actionId, String idempotencyKey, Map<String, Object> actionResult) {
        String key = key(actionId, idempotencyKey);
        if (!StringUtils.hasText(key) || actionResult == null || actionResult.isEmpty()) {
            return;
        }
        store.putIfAbsent(key, new LinkedHashMap<>(actionResult));
    }

    private String key(String actionId, String idempotencyKey) {
        if (!StringUtils.hasText(actionId) || !StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return actionId.trim() + "|" + idempotencyKey.trim();
    }
}

