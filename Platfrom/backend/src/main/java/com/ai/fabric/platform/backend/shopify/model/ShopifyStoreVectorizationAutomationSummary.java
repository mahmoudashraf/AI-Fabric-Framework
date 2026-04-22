package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;
import java.util.List;

public record ShopifyStoreVectorizationAutomationSummary(
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
