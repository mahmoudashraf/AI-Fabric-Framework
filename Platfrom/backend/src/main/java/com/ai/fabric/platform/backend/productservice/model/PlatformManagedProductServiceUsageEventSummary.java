package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceUsageEventSummary(
    String eventType,
    long count
) {
}
