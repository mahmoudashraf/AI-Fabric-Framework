package com.ai.fabric.platform.backend.aiworkspace.model;

import java.util.Locale;

public enum AIWorkspaceConnectionMode {
    PUBLIC_RUNTIME_ANONYMOUS("public-runtime-anonymous"),
    PUBLIC_RUNTIME_AUTHENTICATED("public-runtime-authenticated"),
    BACKEND_MEDIATED_PRIVATE_RUNTIME("backend-mediated-private-runtime");

    private final String manifestValue;

    AIWorkspaceConnectionMode(String manifestValue) {
        this.manifestValue = manifestValue;
    }

    public String manifestValue() {
        return manifestValue;
    }

    public static AIWorkspaceConnectionMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A connection mode is required.");
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return AIWorkspaceConnectionMode.valueOf(normalized);
    }
}
