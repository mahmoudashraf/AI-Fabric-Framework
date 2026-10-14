package com.ai.fabric.platform.backend.aiworkspace.model;

import java.util.Locale;

public enum AIWorkspaceDeliveryMode {
    EMBEDDED,
    STANDALONE;

    public static AIWorkspaceDeliveryMode parse(String value) {
        return value == null || value.isBlank()
            ? EMBEDDED
            : valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String manifestValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
