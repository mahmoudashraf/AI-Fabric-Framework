package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceStoreOverview(
    String platformAccessStatus,
    String platformAccessMessage,
    int totalCount,
    int readyForGoLiveCount,
    int storefrontReadyCount,
    int liveCount,
    int blockedCount,
    String lastWebhookAt
) {
}
