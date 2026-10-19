package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Exact configuration values, not query text or a heuristic storage fallback. */
public final class DeploymentAiStateStorageSelection {
    public static final String FIELD = "aiStateStorageMode";
    private DeploymentAiStateStorageSelection() { }

    public static AiStateStorageMode selected(JsonNode provider) {
        JsonNode value = provider == null ? null : provider.get(FIELD);
        if (value == null) return null;
        if (!value.isTextual()) throw invalid();
        try { return AiStateStorageMode.valueOf(value.textValue()); }
        catch (IllegalArgumentException error) { throw invalid(); }
    }

    public static boolean external(JsonNode provider) {
        return selected(provider) == AiStateStorageMode.CUSTOMER_POSTGRES;
    }

    public static boolean durable(JsonNode provider, JsonNode defaults) {
        AiStateStorageMode mode = selected(provider);
        if (mode != null) return true;
        return defaults != null && "COOLIFY_POSTGRES".equals(defaults.path("runtimeDatabaseMode").asText());
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "AI_STATE_STORAGE_MODE_INVALID");
    }
}
