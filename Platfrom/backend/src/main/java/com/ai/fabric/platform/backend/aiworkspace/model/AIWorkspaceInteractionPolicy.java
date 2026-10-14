package com.ai.fabric.platform.backend.aiworkspace.model;

import java.util.Locale;

public enum AIWorkspaceInteractionPolicy {
    PUBLIC,
    LOGIN_REQUIRED,
    PUBLIC_WITH_PROTECTED_CAPABILITIES;

    public static AIWorkspaceInteractionPolicy parse(String value) {
        return value == null || value.isBlank()
            ? PUBLIC
            : valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String manifestValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public boolean requiresAuthenticationProfile() {
        return this != PUBLIC;
    }
}
