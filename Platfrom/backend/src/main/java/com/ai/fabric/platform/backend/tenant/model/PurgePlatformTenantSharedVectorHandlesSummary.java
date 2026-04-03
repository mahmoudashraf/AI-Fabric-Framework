package com.ai.fabric.platform.backend.tenant.model;

public record PurgePlatformTenantSharedVectorHandlesSummary(
    String tenantId,
    String tenantName,
    int purgedCount,
    int remainingHistoricalHandleCount,
    String status,
    String message
) {
}
