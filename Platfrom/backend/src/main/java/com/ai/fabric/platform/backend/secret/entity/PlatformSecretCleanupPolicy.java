package com.ai.fabric.platform.backend.secret.entity;

public enum PlatformSecretCleanupPolicy {
    KEEP,
    DELETE_ON_HARD_DELETE,
    DELETE_WHEN_UNREFERENCED
}
