package com.ai.fabric.platform.backend.partner.gateway;

import java.time.Instant;
import java.util.List;

public record PartnerShopifyStoreReadModel(
    String storeConnectionId,
    String shopDomain,
    String displayName,
    String installStatus,
    String knowledgeSyncStatus,
    String sourceReadinessStatus,
    String widgetStatus,
    Instant lastSyncAt,
    Instant lastWebhookAt,
    List<String> enabledSourceCategories,
    List<String> enabledSurfaces
) {
}
