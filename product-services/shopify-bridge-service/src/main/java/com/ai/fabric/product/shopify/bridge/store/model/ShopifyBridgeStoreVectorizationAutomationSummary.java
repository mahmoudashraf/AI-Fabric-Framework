package com.ai.fabric.product.shopify.bridge.store.model;

import java.time.Instant;
import java.util.List;

public record ShopifyBridgeStoreVectorizationAutomationSummary(
    boolean autoIndexingHealthy,
    int queuedEvents,
    int leasedEvents,
    int dispatchedEvents,
    int skippedEvents,
    int failedEvents,
    int deadLetteredEvents,
    Instant lastAutoEventAt,
    Instant lastSuccessfulAutoIndexAt,
    Instant lastFailedAutoIndexAt,
    String lastAutoRunId,
    List<String> degradedReasons
) {
}
