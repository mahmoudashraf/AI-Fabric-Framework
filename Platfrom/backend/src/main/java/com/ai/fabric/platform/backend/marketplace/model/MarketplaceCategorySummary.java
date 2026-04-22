package com.ai.fabric.platform.backend.marketplace.model;

public record MarketplaceCategorySummary(
    String id,
    String label,
    long pluginCount
) {
}
