package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceStoreSupportSubscriptionSummary(
    String subscriptionId,
    String name,
    String status,
    String tierKey,
    boolean active
) {
}
