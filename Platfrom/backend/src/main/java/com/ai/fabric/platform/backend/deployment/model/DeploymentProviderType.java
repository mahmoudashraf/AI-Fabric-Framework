package com.ai.fabric.platform.backend.deployment.model;

import java.util.Locale;

public enum DeploymentProviderType {
    RAILWAY_API,
    RAILWAY_STUB,
    COOLIFY;

    public static DeploymentProviderType fromLegacyMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return RAILWAY_STUB;
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "RAILWAY", "RAILWAY_API" -> RAILWAY_API;
            case "STUB", "RAILWAY_STUB" -> RAILWAY_STUB;
            case "COOLIFY" -> COOLIFY;
            default -> throw new IllegalArgumentException("Unsupported deployment provider type: " + mode);
        };
    }

    public boolean matchesLegacyMode(String mode) {
        try {
            return this == fromLegacyMode(mode);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String legacyTarget() {
        return name();
    }
}
