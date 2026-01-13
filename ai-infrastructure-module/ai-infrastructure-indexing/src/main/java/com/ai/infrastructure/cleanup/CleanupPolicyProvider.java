package com.ai.infrastructure.cleanup;

/**
 * Resolves the cleanup strategy and retention window for a given entity type.
 */
public interface CleanupPolicyProvider {

    CleanupStrategy getStrategy(String entityType);

    int getRetentionDays(String entityType);
}
