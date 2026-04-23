package com.ai.fabric.platform.backend.productservice.model;

import java.util.List;

public record PlatformManagedProductServiceStoreUsageRoiSummary(
    String status,
    String message,
    long shopperAssistSignals,
    long decisionSupportSignals,
    long governedActionGrants,
    long governedActionCompletions,
    long governedActionFailures,
    int activeSurfaceCount,
    List<String> strongestSurfaceLabels,
    List<String> recommendations
) {
}
