package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceStoreUsageSurfaceJourneySummary(
    String surfaceId,
    String label,
    long shopperQuestions,
    long shopperInteractions,
    long readActions,
    long governedActionGrants,
    long governedActionCompletions,
    long governedActionFailures
) {
}
