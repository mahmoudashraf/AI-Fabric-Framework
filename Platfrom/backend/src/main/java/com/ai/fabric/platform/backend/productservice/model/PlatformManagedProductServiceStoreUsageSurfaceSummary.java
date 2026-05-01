package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceStoreUsageSurfaceSummary(
    String surfaceId,
    String label,
    long count
) {
}
