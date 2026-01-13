package com.ai.infrastructure.cleanup;

/**
 * Supported cleanup approaches for aged AI search entities.
 */
public enum CleanupStrategy {
    SOFT_DELETE,
    ARCHIVE,
    HARD_DELETE,
    CASCADE
}
