package com.ai.infrastructure.intent.action;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Represents an action that was proposed to the user and is waiting for explicit confirmation.
 */
public record PendingAction(
    String action,
    Map<String, Object> actionParams,
    String description,
    Instant createdAt
) {

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action);
        out.put("params", actionParams != null ? actionParams : Collections.emptyMap());
        out.put("description", description);
        out.put("timestamp", createdAt != null ? createdAt.toString() : null);
        return Collections.unmodifiableMap(out);
    }

    @SuppressWarnings("unchecked")
    public static PendingAction fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String action = raw.get("action") != null ? String.valueOf(raw.get("action")) : null;
        String description = raw.get("description") != null ? String.valueOf(raw.get("description")) : null;

        Instant createdAt = null;
        Object createdAtRaw = raw.get("timestamp");
        if (createdAtRaw instanceof String str && StringUtils.hasText(str)) {
            try {
                createdAt = Instant.parse(str.trim());
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> params = null;
        Object paramsRaw = raw.get("params");
        if (paramsRaw instanceof Map<?, ?> map) {
            params = (Map<String, Object>) map;
        }

        if (!StringUtils.hasText(action)) {
            return null;
        }
        return new PendingAction(action.trim(), params, description, createdAt);
    }
}
