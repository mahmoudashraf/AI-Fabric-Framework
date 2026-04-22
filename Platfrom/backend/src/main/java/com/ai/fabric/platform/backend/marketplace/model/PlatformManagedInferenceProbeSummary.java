package com.ai.fabric.platform.backend.marketplace.model;

public record PlatformManagedInferenceProbeSummary(
    String key,
    String label,
    String status,
    String endpoint,
    String method,
    String message,
    String checkedAt
) {
}
