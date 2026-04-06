package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentTenantScopedVectorRegistrySummary(
    String status,
    String recordId,
    int activeRecordCount,
    int historicalRecordCount,
    Instant lastUpdatedAt,
    String cleanupReadinessStatus,
    String cleanupReadinessMessage,
    String message
) {
}
