package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record MarketplaceMcpImportDraftSummary(
    String pluginId,
    String pluginVersionId,
    String version,
    String status,
    JsonNode manifest,
    MarketplaceMcpDiscoverySummary discovery,
    Instant createdAt
) {
}
